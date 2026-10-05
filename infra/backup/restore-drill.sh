#!/usr/bin/env bash
set -Eeuo pipefail

# Restore into a disposable PostgreSQL target. This script is intentionally never pointed at the
# application's production database automatically; the operator must explicitly acknowledge the drill.
DUMP_FILE="${1:-}"
TARGET_DATABASE_URL="${TARGET_DATABASE_URL:-}"
DRILL_CONFIRM="${DRILL_CONFIRM:-}"

if [[ -z "$DUMP_FILE" || ! -f "$DUMP_FILE" ]]; then
  echo "Usage: DRILL_CONFIRM=RESTORE-DRILL TARGET_DATABASE_URL='<jdbc-or-libpq-url>' $0 /path/to/backup.dump" >&2
  exit 2
fi
if [[ "$DRILL_CONFIRM" != "RESTORE-DRILL" ]]; then
  echo "Refusing restore drill without DRILL_CONFIRM=RESTORE-DRILL." >&2
  exit 2
fi
if [[ -z "$TARGET_DATABASE_URL" ]]; then
  echo "TARGET_DATABASE_URL is required and must point to a disposable restore database." >&2
  exit 2
fi

# Accept either a libpq URL or a JDBC PostgreSQL URL and normalize it for pg_restore.
URL="${TARGET_DATABASE_URL#jdbc:}"
if [[ "$URL" != postgres://* && "$URL" != postgresql://* ]]; then
  echo "TARGET_DATABASE_URL must be a PostgreSQL JDBC/libpq URL." >&2
  exit 2
fi

command -v pg_restore >/dev/null 2>&1 || { echo "pg_restore is required on the restore host." >&2; exit 1; }
command -v psql >/dev/null 2>&1 || { echo "psql is required on the restore host." >&2; exit 1; }

sha256sum -c "${DUMP_FILE}.sha256"
pg_restore "$URL" --clean --if-exists --no-owner --exit-on-error "$DUMP_FILE"

# A lightweight post-restore integrity probe. The application schema must have core payment/ticket tables.
for table in events orders payments tickets refunds; do
  value="$(psql "$URL" -Atqc "SELECT to_regclass('$table') IS NOT NULL")"
  [[ "$value" == "t" ]] || { echo "Restored database is missing required table: $table" >&2; exit 1; }
done

migration_count="$(psql "$URL" -Atqc 'SELECT count(*) FROM flyway_schema_history')"
[[ "$migration_count" =~ ^[1-9][0-9]*$ ]] || { echo "Flyway history is missing after restore." >&2; exit 1; }

echo "Enterprise restore drill passed: $DUMP_FILE"
echo "Verified core tables and Flyway history count: $migration_count"
