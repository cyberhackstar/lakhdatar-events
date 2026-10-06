#!/usr/bin/env bash
set -Eeuo pipefail

# Enterprise database qualification:
# 1) migrate a completely empty database to the latest schema;
# 2) migrate a second empty database only to the pre-enterprise baseline and then upgrade it;
# 3) validate the final schema and prove the latest version is installed with no failed migrations.
: "${POSTGRES_IMAGE:?POSTGRES_IMAGE must be a pinned postgres image digest (e.g. postgres:17-alpine@sha256:...)}"
: "${FLYWAY_IMAGE:?FLYWAY_IMAGE must be a pinned Flyway image digest (e.g. flyway/flyway:...@sha256:...)}"
EXPECTED_LATEST_MIGRATION="${EXPECTED_LATEST_MIGRATION:-}"

[[ "$POSTGRES_IMAGE" == *@sha256:* ]] || { echo 'POSTGRES_IMAGE must be pinned by digest' >&2; exit 2; }
[[ "$FLYWAY_IMAGE" == *@sha256:* ]] || { echo 'FLYWAY_IMAGE must be pinned by digest' >&2; exit 2; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATIONS="$ROOT/backend/src/main/resources/db/migration"
LATEST_MIGRATION="$(find "$MIGRATIONS" -maxdepth 1 -type f -name 'V*_*.sql' -printf '%f\n' | sed -n 's/^V\([0-9][0-9]*\)__.*/\1/p' | sort -n | tail -1)"
[[ "$LATEST_MIGRATION" =~ ^[0-9]+$ ]] || { echo 'Unable to determine latest Flyway migration from source tree' >&2; exit 2; }
if [[ -n "$EXPECTED_LATEST_MIGRATION" ]]; then
  [[ "$EXPECTED_LATEST_MIGRATION" =~ ^[0-9]+$ ]] || { echo 'EXPECTED_LATEST_MIGRATION must be numeric' >&2; exit 2; }
  [[ "$EXPECTED_LATEST_MIGRATION" == "$LATEST_MIGRATION" ]] || { echo "EXPECTED_LATEST_MIGRATION=$EXPECTED_LATEST_MIGRATION is stale; source tree latest migration is V$LATEST_MIGRATION" >&2; exit 2; }
else
  EXPECTED_LATEST_MIGRATION="$LATEST_MIGRATION"
fi


REPORT_DIR="${REPORT_DIR:-$ROOT/certification-evidence/database}"
NETWORK="lk-db-cert-${GITHUB_RUN_ID:-local}-$$"
CONTAINER="lk-db-cert-${GITHUB_RUN_ID:-local}-$$"
DB_PASSWORD="$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(24))
PY
)"
cleanup() {
  set +e
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mkdir -p "$REPORT_DIR"
docker network create "$NETWORK" >/dev/null
docker run -d --name "$CONTAINER" --network "$NETWORK" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e POSTGRES_DB=postgres \
  "$POSTGRES_IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$CONTAINER" pg_isready -U postgres -d postgres >/dev/null 2>&1; then break; fi
  sleep 2
done
docker exec "$CONTAINER" pg_isready -U postgres -d postgres >/dev/null

docker exec "$CONTAINER" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE lk_fresh; CREATE DATABASE lk_upgrade;' >/dev/null

flyway() {
  local db="$1"; shift
  docker run --rm --network "$NETWORK" \
    -v "$MIGRATIONS:/flyway/sql:ro" \
    "$FLYWAY_IMAGE" \
    -url="jdbc:postgresql://$CONTAINER:5432/$db" \
    -user=postgres -password="$DB_PASSWORD" \
    -locations=filesystem:/flyway/sql \
    -connectRetries=30 \
    "$@"
}

{
  echo '=== Fresh database: latest migration ==='
  flyway lk_fresh migrate
  flyway lk_fresh validate
  echo '=== Upgrade database: baseline through V31, then latest ==='
  flyway lk_upgrade -target="${UPGRADE_BASELINE_MIGRATION:-31}" migrate
  flyway lk_upgrade migrate
  flyway lk_upgrade validate
  echo '=== Re-run latest migration to prove idempotent no-op ==='
  flyway lk_upgrade migrate
  echo '=== Schema assertions ==='
  docker exec "$CONTAINER" psql -U postgres -d lk_fresh -Atc "SELECT max(version) FROM flyway_schema_history WHERE success = true;" > "$REPORT_DIR/fresh-latest-version.txt"
  docker exec "$CONTAINER" psql -U postgres -d lk_upgrade -Atc "SELECT max(version) FROM flyway_schema_history WHERE success = true;" > "$REPORT_DIR/upgrade-latest-version.txt"
  docker exec "$CONTAINER" psql -U postgres -d lk_fresh -Atc "SELECT count(*) FROM flyway_schema_history WHERE success = false;" > "$REPORT_DIR/fresh-failed-count.txt"
  docker exec "$CONTAINER" psql -U postgres -d lk_upgrade -Atc "SELECT count(*) FROM flyway_schema_history WHERE success = false;" > "$REPORT_DIR/upgrade-failed-count.txt"
  fresh_latest="$(tr -d '[:space:]' < "$REPORT_DIR/fresh-latest-version.txt")"
  upgrade_latest="$(tr -d '[:space:]' < "$REPORT_DIR/upgrade-latest-version.txt")"
  fresh_failed="$(tr -d '[:space:]' < "$REPORT_DIR/fresh-failed-count.txt")"
  upgrade_failed="$(tr -d '[:space:]' < "$REPORT_DIR/upgrade-failed-count.txt")"
  [[ "$fresh_latest" == "$EXPECTED_LATEST_MIGRATION" ]] || { echo "Fresh database ended at migration $fresh_latest, expected $EXPECTED_LATEST_MIGRATION"; exit 1; }
  [[ "$upgrade_latest" == "$EXPECTED_LATEST_MIGRATION" ]] || { echo "Upgrade database ended at migration $upgrade_latest, expected $EXPECTED_LATEST_MIGRATION"; exit 1; }
  [[ "$fresh_failed" == 0 && "$upgrade_failed" == 0 ]] || { echo 'Flyway schema history contains failed migrations'; exit 1; }
} 2>&1 | tee "$REPORT_DIR/flyway-qualification.log"

echo "FLYWAY_ENTERPRISE_QUALIFICATION_PASSED"
