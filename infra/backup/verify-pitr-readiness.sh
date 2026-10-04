#!/usr/bin/env bash
set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGDATABASE:?PGDATABASE is required}"

psql -v ON_ERROR_STOP=1 -Atqc "select 'wal_level=' || current_setting('wal_level');"
psql -v ON_ERROR_STOP=1 -Atqc "select 'archive_mode=' || current_setting('archive_mode');"
psql -v ON_ERROR_STOP=1 -Atqc "select 'archive_timeout=' || current_setting('archive_timeout');"
psql -v ON_ERROR_STOP=1 -Atqc "select 'archive_command=' || current_setting('archive_command');"

wal_level="$(psql -Atqc "show wal_level")"
archive_mode="$(psql -Atqc "show archive_mode")"
archive_command="$(psql -Atqc "show archive_command")"

[[ "$wal_level" == "replica" || "$wal_level" == "logical" ]] || { echo 'ERROR: wal_level must be replica or logical' >&2; exit 1; }
[[ "$archive_mode" == "on" ]] || { echo 'ERROR: archive_mode is off' >&2; exit 1; }
[[ -n "$archive_command" && "$archive_command" != "(disabled)" ]] || { echo 'ERROR: archive_command is not configured' >&2; exit 1; }

echo 'PITR readiness configuration is enabled. Validate object-storage delivery and restore drills separately.'
