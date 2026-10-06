#!/usr/bin/env bash
set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGDATABASE:?PGDATABASE is required}"

psql -v ON_ERROR_STOP=1 -Atqc "select 'wal_level=' || current_setting('wal_level');"
psql -v ON_ERROR_STOP=1 -Atqc "select 'archive_mode=' || current_setting('archive_mode');"
psql -v ON_ERROR_STOP=1 -Atqc "select 'archive_timeout=' || current_setting('archive_timeout');"
psql -v ON_ERROR_STOP=1 -Atqc "select 'archive_command=' || current_setting('archive_command');"
psql -v ON_ERROR_STOP=1 -Atqc "select 'archived_count=' || archived_count || ',failed_count=' || failed_count || ',last_archived_wal=' || coalesce(last_archived_wal,'') || ',last_archived_time=' || coalesce(last_archived_time::text,'') from pg_stat_archiver;"

wal_level="$(psql -Atqc "show wal_level")"
archive_mode="$(psql -Atqc "show archive_mode")"
archive_command="$(psql -Atqc "show archive_command")"

[[ "$wal_level" == "replica" || "$wal_level" == "logical" ]] || { echo 'ERROR: wal_level must be replica or logical' >&2; exit 1; }
[[ "$archive_mode" == "on" ]] || { echo 'ERROR: archive_mode is off' >&2; exit 1; }
[[ -n "$archive_command" && "$archive_command" != "(disabled)" ]] || { echo 'ERROR: archive_command is not configured' >&2; exit 1; }


archived_count="$(psql -Atqc "select archived_count from pg_stat_archiver")"
failed_count="$(psql -Atqc "select failed_count from pg_stat_archiver")"
last_archived_time="$(psql -Atqc "select coalesce(extract(epoch from (now()-last_archived_time)), 999999999) from pg_stat_archiver")"
[[ "$archived_count" =~ ^[0-9]+$ ]] || { echo 'ERROR: unable to read archived_count' >&2; exit 1; }
[[ "$failed_count" =~ ^[0-9]+$ ]] || { echo 'ERROR: unable to read failed_count' >&2; exit 1; }
max_age="${PITR_MAX_ARCHIVE_AGE_SECONDS:-900}"
[[ "$max_age" =~ ^[1-9][0-9]*$ ]] || { echo 'ERROR: PITR_MAX_ARCHIVE_AGE_SECONDS must be positive' >&2; exit 1; }
python3 - "$last_archived_time" "$max_age" <<'PY2'
import sys
age=float(sys.argv[1]); limit=int(sys.argv[2])
if age > limit:
    raise SystemExit(f'ERROR: latest archived WAL is {age:.0f}s old; limit is {limit}s')
PY2

if [[ "$failed_count" -gt 0 && "${PITR_ALLOW_HISTORICAL_FAILURES:-false}" != true ]]; then
  echo "ERROR: pg_stat_archiver reports $failed_count historical archive failures; set PITR_ALLOW_HISTORICAL_FAILURES=true only after operational review" >&2
  exit 1
fi

echo "PITR live archive delivery verified; latest WAL archive age=${last_archived_time}s"
