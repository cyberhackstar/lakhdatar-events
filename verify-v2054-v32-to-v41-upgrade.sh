#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE="lakhdatar-r2-restore-20261013T161157Z-3592169"
DB="restore_validation"
STAGING="/home/ubuntu/apps/lakhdatar-events-staging"
EXPECTED_SHA="fff2632c45dcc9b5b14b5e40190f60d876f8e147"
SQL_DIR="$STAGING/backend/src/main/resources/db/migration"
FLYWAY="flyway/flyway:13.8.0-alpine@sha256:533745f3b566788a3ba7e5bb238a4596cf96a3a4c4531b5efca20962ef5ff3b8"

[[ -d "$SQL_DIR" ]] || { echo "Staging migration directory missing; refusing."; exit 2; }
[[ "$(tr -d '\r\n' < "$STAGING/.deploy-current")" == "$EXPECTED_SHA" ]] || { echo "Staging SHA mismatch; refusing."; exit 2; }
[[ "$(find "$SQL_DIR" -maxdepth 1 -type f -name 'V*_*.sql' -printf '%f\n' | sed -n 's/^V\([0-9][0-9]*\)__.*/\1/p' | sort -n | tail -1)" == "41" ]] || { echo "Expected migration V41; refusing."; exit 2; }
docker exec "$SOURCE" pg_isready -q -U postgres -d "$DB" || { echo "Isolated source database not ready; refusing."; exit 2; }

ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
NET="lk-v2054-upgrade-net-$ID"
TARGET="lk-v2054-upgrade-db-$ID"
VOL="lk-v2054-upgrade-data-$ID"
REPORT="/home/ubuntu/ops-snapshots/pitr-stream/evidence/v2054-v32-upgrade-$ID"
docker container inspect "$TARGET" >/dev/null 2>&1 && { echo "Temporary container name already exists; refusing."; exit 2; } || true
docker volume inspect "$VOL" >/dev/null 2>&1 && { echo "Temporary volume name already exists; refusing."; exit 2; } || true
docker network inspect "$NET" >/dev/null 2>&1 && { echo "Temporary network name already exists; refusing."; exit 2; } || true
mkdir -p "$REPORT"
cleanup() {
  status=$?
  trap - EXIT
  set +e
  [[ -f "$REPORT/run.log" ]] && sha256sum "$REPORT/run.log" > "$REPORT/run.log.sha256"
  docker rm -f "$TARGET" >/dev/null 2>&1 || true
  docker volume rm "$VOL" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT
exec > >(tee -a "$REPORT/run.log") 2>&1

echo "TEST_SCOPE=disposable clone of isolated V32 restore; migration compatibility only"
echo "RELEASE_SHA=$EXPECTED_SHA"
echo "REPORT_DIR=$REPORT"
docker network create --internal "$NET" >/dev/null
docker volume create "$VOL" >/dev/null
docker run -d --name "$TARGET" --network "$NET" \
  --mount "type=volume,source=$VOL,target=/var/lib/postgresql/data" \
  -e POSTGRES_DB="$DB" -e POSTGRES_USER=postgres -e POSTGRES_HOST_AUTH_METHOD=trust \
  postgres:16-alpine >/dev/null

ready=0
for _ in $(seq 1 60); do
  if docker exec "$TARGET" pg_isready -q -U postgres -d "$DB"; then ready=1; break; fi
  sleep 2
done
[[ "$ready" == 1 ]] || { echo "Temporary target failed readiness."; exit 1; }

echo "=== Copy isolated restore into new temporary target ==="
docker exec "$SOURCE" pg_dump -U postgres -d "$DB" -Fc --no-owner --no-acl \
  | docker exec -i "$TARGET" pg_restore -U postgres -d "$DB" --clean --if-exists --no-owner --no-privileges --exit-on-error

BASE="$(docker exec "$TARGET" psql -X -U postgres -d "$DB" -At -c "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;")"
[[ "$BASE" == "32" ]] || { echo "Expected V32 baseline; found ${BASE:-unknown}. Refusing."; exit 1; }
echo "BASELINE_VERSION=$BASE"

flyway() {
  docker run --rm --network "$NET" -v "$SQL_DIR:/flyway/sql:ro" "$FLYWAY" \
    "-url=jdbc:postgresql://$TARGET:5432/$DB" -user=postgres \
    -locations=filesystem:/flyway/sql -connectRetries=30 "$@"
}

echo "=== Applying migrations to disposable clone ==="
flyway migrate
echo "=== Validating all migrations ==="
flyway validate

echo "=== Final migration summary ==="
docker exec "$TARGET" psql -X -U postgres -d "$DB" -P pager=off \
  -c "SELECT (SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1) AS latest_version,
             count(*) AS history_rows,
             count(*) FILTER (WHERE success) AS successful,
             count(*) FILTER (WHERE NOT success) AS failed
      FROM flyway_schema_history;"
echo "=== Critical aggregate counts ==="
docker exec "$TARGET" psql -X -U postgres -d "$DB" -P pager=off \
  -c "SELECT 'events' AS table_name, count(*) AS row_count FROM events
      UNION ALL SELECT 'orders', count(*) FROM orders
      UNION ALL SELECT 'payments', count(*) FROM payments
      UNION ALL SELECT 'refunds', count(*) FROM refunds
      UNION ALL SELECT 'tickets', count(*) FROM tickets
      UNION ALL SELECT 'ticket_checkins', count(*) FROM ticket_checkins
      UNION ALL SELECT 'financial_ledger_entries', count(*) FROM financial_ledger_entries
      ORDER BY table_name;"
LATEST="$(docker exec "$TARGET" psql -X -U postgres -d "$DB" -At -c "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;")"
FAILED="$(docker exec "$TARGET" psql -X -U postgres -d "$DB" -At -c "SELECT count(*) FROM flyway_schema_history WHERE NOT success;")"
[[ "$LATEST" == "41" && "$FAILED" == "0" ]] || { echo "MIGRATION_UPGRADE_TEST=FAIL"; exit 1; }
echo "MIGRATION_UPGRADE_TEST=PASS"
echo "NOTE=This is migration compatibility evidence only, not a full disaster-recovery drill or production certification."
echo "Sanitized migration log will be retained at $REPORT/run.log; temporary database, volume, and internal network are removed on exit."
