#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXPECTED_ROOT="/home/ubuntu/apps/lakhdatar-events"
if [[ "$ROOT" != "$EXPECTED_ROOT" && "${DEPLOY_ALLOW_ANY_ROOT:-}" != "1" ]]; then
  echo "Refusing to deploy from $ROOT. Production lives in $EXPECTED_ROOT." >&2
  exit 2
fi

# Docker Compose owns parsing of the complete .env file.  The deploy script only needs a
# small set of deployment controls and must never `source` arbitrary dotenv values: values such as
# MAIL_FROM="Name <sender@domain>" are valid dotenv syntax but are not valid bare shell syntax.
dotenv_get() {
  local key="$1" line value
  line="$(grep -E "^${key}=" "$ROOT/.env" | tail -n 1 || true)"
  [[ -n "$line" ]] || return 0
  value="${line#*=}"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

COMPOSE=(docker compose --env-file "$ROOT/.env" -f "$ROOT/infra/docker-compose.prod.yml")
CURRENT="$ROOT/.deploy-current"
PREVIOUS="$ROOT/.deploy-previous"
TAG="${1:-}"
LOCAL_URL="http://127.0.0.1:4002"
PUBLIC_HOST="${PUBLIC_HOST:-$(dotenv_get PUBLIC_HOST)}"
PUBLIC_HOST="${PUBLIC_HOST:-events.neelastack.com}"
[[ "$PUBLIC_HOST" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "PUBLIC_HOST must be a hostname without scheme/path" >&2; exit 2; }
[[ "$PUBLIC_HOST" != .* && "$PUBLIC_HOST" != *..* && "$PUBLIC_HOST" != *.- && "$PUBLIC_HOST" != *-. ]] || { echo "PUBLIC_HOST has invalid hostname syntax" >&2; exit 2; }
PRODUCTION_TOPOLOGY="${PRODUCTION_TOPOLOGY:-$(dotenv_get PRODUCTION_TOPOLOGY)}"
PRODUCTION_TOPOLOGY="${PRODUCTION_TOPOLOGY:-single-node}"
ALLOW_SINGLE_NODE_PRODUCTION="${ALLOW_SINGLE_NODE_PRODUCTION:-$(dotenv_get ALLOW_SINGLE_NODE_PRODUCTION)}"
if [[ "$PRODUCTION_TOPOLOGY" == "enterprise-ha" ]]; then
  echo "Refusing the single-node deploy script for PRODUCTION_TOPOLOGY=enterprise-ha. Deploy infra/ha/docker-compose.ha.example.yml on at least two VMs behind health-checked ingress." >&2
  exit 2
fi
if [[ "$ALLOW_SINGLE_NODE_PRODUCTION" != "true" ]]; then
  echo "Single-node production requires explicit ALLOW_SINGLE_NODE_PRODUCTION=true. For enterprise HA use the HA deployment profile instead." >&2
  exit 2
fi

BACKEND_READINESS_TIMEOUT_SECONDS="${BACKEND_READINESS_TIMEOUT_SECONDS:-$(dotenv_get BACKEND_READINESS_TIMEOUT_SECONDS)}"
BACKEND_READINESS_TIMEOUT_SECONDS="${BACKEND_READINESS_TIMEOUT_SECONDS:-300}"
BACKEND_READINESS_INTERVAL_SECONDS="${BACKEND_READINESS_INTERVAL_SECONDS:-$(dotenv_get BACKEND_READINESS_INTERVAL_SECONDS)}"
BACKEND_READINESS_INTERVAL_SECONDS="${BACKEND_READINESS_INTERVAL_SECONDS:-3}"
WEB_READINESS_TIMEOUT_SECONDS="${WEB_READINESS_TIMEOUT_SECONDS:-$(dotenv_get WEB_READINESS_TIMEOUT_SECONDS)}"
WEB_READINESS_TIMEOUT_SECONDS="${WEB_READINESS_TIMEOUT_SECONDS:-180}"
WEB_READINESS_INTERVAL_SECONDS="${WEB_READINESS_INTERVAL_SECONDS:-$(dotenv_get WEB_READINESS_INTERVAL_SECONDS)}"
WEB_READINESS_INTERVAL_SECONDS="${WEB_READINESS_INTERVAL_SECONDS:-3}"

[[ -n "$TAG" ]] || { echo "Usage: $0 <immutable-image-tag>" >&2; exit 2; }
[[ -f "$ROOT/.env" ]] || { echo "Missing $ROOT/.env" >&2; exit 2; }
PREVIOUS_TAG="$(cat "$CURRENT" 2>/dev/null || true)"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-$(dotenv_get IMAGE_NAMESPACE)}"
[[ -n "${IMAGE_NAMESPACE:-}" ]] || { echo "IMAGE_NAMESPACE is required (e.g. ghcr.io/owner)" >&2; exit 2; }
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "Release image tag must be a 40-character Git SHA" >&2; exit 2; }

for setting in BACKEND_READINESS_TIMEOUT_SECONDS BACKEND_READINESS_INTERVAL_SECONDS WEB_READINESS_TIMEOUT_SECONDS WEB_READINESS_INTERVAL_SECONDS; do
  value="${!setting}"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || { echo "$setting must be a positive integer" >&2; exit 2; }
done

# Validate the exact production Compose model before touching services.
IMAGE_TAG="$TAG" docker compose --env-file "$ROOT/.env" -f "$ROOT/infra/docker-compose.prod.yml" config >/dev/null

DEPLOY_STAGE=0

rollback_previous() {
  if [[ -n "$PREVIOUS_TAG" && "$PREVIOUS_TAG" != "$TAG" && "$DEPLOY_STAGE" -lt 4 ]]; then
    echo "Deployment failed before Flyway-backed application startup. Rolling back to $PREVIOUS_TAG" >&2
    IMAGE_TAG="$PREVIOUS_TAG" "${COMPOSE[@]}" up -d backend web edge || true
    echo "$PREVIOUS_TAG" > "$CURRENT"
  elif [[ "$DEPLOY_STAGE" -ge 4 ]]; then
    echo "Deployment failed after backend startup; automatic image rollback is intentionally disabled because Flyway may have changed the schema. Use an expand/contract-compatible release rollback." >&2
  fi
}
trap rollback_previous ERR

print_backend_diagnostics() {
  echo "--- backend compose status ---" >&2
  "${COMPOSE[@]}" ps backend >&2 || true
  echo "--- backend container state/health ---" >&2
  docker inspect --format '{{json .State}}' lakhdatar-backend >&2 || true
  echo "--- backend logs (tail 250) ---" >&2
  "${COMPOSE[@]}" logs --no-color --tail=250 backend >&2 || true
}

wait_for_backend_readiness() {
  local deadline=$((SECONDS + BACKEND_READINESS_TIMEOUT_SECONDS))
  local attempt=0
  local response=''
  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    if ! docker inspect --format '{{.State.Status}}' lakhdatar-backend >/dev/null 2>&1; then
      echo "Backend container is not present yet (attempt $attempt)." >&2
      sleep "$BACKEND_READINESS_INTERVAL_SECONDS"
      continue
    fi
    local state
    state="$(docker inspect --format '{{.State.Status}}' lakhdatar-backend 2>/dev/null || true)"
    if [[ "$state" == "exited" || "$state" == "dead" ]]; then
      echo "Backend container entered terminal state '$state'." >&2
      print_backend_diagnostics
      return 1
    fi
    if response="$(docker exec lakhdatar-backend wget -qO- --timeout=5 http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null)"; then
      if [[ "$response" == *'"status":"UP"'* ]]; then
        echo "Backend readiness passed on attempt $attempt."
        return 0
      fi
      echo "Backend readiness endpoint responded but is not UP yet (attempt $attempt): $response" >&2
    else
      echo "Backend readiness endpoint not available yet (attempt $attempt)." >&2
    fi
    sleep "$BACKEND_READINESS_INTERVAL_SECONDS"
  done
  echo "Backend readiness timed out after ${BACKEND_READINESS_TIMEOUT_SECONDS}s." >&2
  print_backend_diagnostics
  return 1
}

print_edge_diagnostics() {
  echo "--- edge container state/health ---" >&2
  docker inspect --format '{{json .State}}' lakhdatar-edge >&2 || true
  echo "--- edge port bindings ---" >&2
  docker inspect --format '{{json .NetworkSettings.Ports}}' lakhdatar-edge >&2 || true
  echo "--- edge nginx config test ---" >&2
  docker exec lakhdatar-edge nginx -t >&2 || true
  echo "--- edge internal health ---" >&2
  docker exec lakhdatar-edge wget -S -O- --timeout=5 http://127.0.0.1:8080/edge-health >&2 || true
  echo "--- host port 4002 ---" >&2
  curl -v --max-time 10 "$LOCAL_URL/edge-health" >&2 || true
  echo "--- host listener 4002 ---" >&2
  ss -lntp 2>/dev/null | grep ':4002' >&2 || true
  echo "--- edge logs (tail 250) ---" >&2
  docker logs --tail=250 lakhdatar-edge >&2 || true
}

wait_for_edge_readiness() {
  local deadline=$((SECONDS + WEB_READINESS_TIMEOUT_SECONDS))
  local attempt=0
  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    local state='' health='' restart_count='' exit_code=''
    if ! docker inspect lakhdatar-edge >/dev/null 2>&1; then
      echo "Edge container is not present yet (attempt $attempt)." >&2
      sleep "$WEB_READINESS_INTERVAL_SECONDS"
      continue
    fi

    state="$(docker inspect --format '{{.State.Status}}' lakhdatar-edge 2>/dev/null || true)"
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' lakhdatar-edge 2>/dev/null || true)"
    restart_count="$(docker inspect --format '{{.RestartCount}}' lakhdatar-edge 2>/dev/null || true)"
    exit_code="$(docker inspect --format '{{.State.ExitCode}}' lakhdatar-edge 2>/dev/null || true)"

    if [[ "$state" == "restarting" || "$state" == "exited" || "$state" == "dead" ]]; then
      echo "Edge container is not runnable: state=$state health=$health restart_count=$restart_count exit_code=$exit_code." >&2
      print_edge_diagnostics
      return 1
    fi

    if [[ "$state" == "running" ]]; then
      if docker exec lakhdatar-edge wget -qO- --timeout=5 http://127.0.0.1:8080/edge-health >/dev/null 2>&1; then
        if curl -fsS --max-time 10 "$LOCAL_URL/edge-health" >/dev/null 2>&1; then
          echo "Edge readiness passed on attempt $attempt."
          return 0
        fi
        echo "Edge is running internally but host port 4002 is not ready yet (attempt $attempt)." >&2
      else
        echo "Edge container is running but internal /edge-health is not ready yet (attempt $attempt)." >&2
      fi
    else
      echo "Edge container state is '$state' (attempt $attempt); waiting." >&2
    fi

    sleep "$WEB_READINESS_INTERVAL_SECONDS"
  done

  echo "Edge readiness timed out after ${WEB_READINESS_TIMEOUT_SECONDS}s." >&2
  print_edge_diagnostics
  return 1
}

echo "[1/7] Pre-deployment database backup"
BACKUP_REMOTE_REQUIRED="$(dotenv_get BACKUP_REMOTE_REQUIRED)"
BACKUP_REMOTE_REQUIRED="${BACKUP_REMOTE_REQUIRED:-true}"
BACKUP_REMOTE_URI="$(dotenv_get BACKUP_REMOTE_URI)"
BACKUP_S3_ENDPOINT_URL="$(dotenv_get BACKUP_S3_ENDPOINT_URL)"
BACKUP_S3_SSE="$(dotenv_get BACKUP_S3_SSE)"
BACKUP_S3_KMS_KEY_ID="$(dotenv_get BACKUP_S3_KMS_KEY_ID)"
export BACKUP_REMOTE_REQUIRED BACKUP_REMOTE_URI BACKUP_S3_ENDPOINT_URL BACKUP_S3_SSE BACKUP_S3_KMS_KEY_ID
POSTGRES_VOLUME_EXISTS="$(docker volume inspect lakhdatar_pg_data >/dev/null 2>&1 && echo 1 || echo 0)"
if [[ -n "$PREVIOUS_TAG" || "$POSTGRES_VOLUME_EXISTS" == "1" ]]; then
  if [[ -n "$PREVIOUS_TAG" ]]; then
    echo "Existing deployment detected ($PREVIOUS_TAG): backup is mandatory and fail-closed."
  else
    echo "Existing PostgreSQL data volume detected: backup is mandatory and fail-closed."
  fi
  "$ROOT/infra/backup/backup-postgres.sh"
else
  echo "No previous deployment marker or PostgreSQL data volume found: allowing first-install backup exception."
  "$ROOT/infra/backup/backup-postgres.sh" --allow-missing
fi
if [[ -n "$PREVIOUS_TAG" || -n "$(find "${BACKUP_DIR:-$ROOT/infra/backups}" -maxdepth 1 -name 'lakhdatar-*.dump' -print -quit 2>/dev/null)" ]]; then
  BACKUP_DIR="${BACKUP_DIR:-$ROOT/infra/backups}" "$ROOT/infra/backup/verify-latest-backup.sh"
fi

echo "[2/7] Pulling immutable release $TAG"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" pull postgres redis backend web edge

echo "[3/7] Starting data services"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d postgres redis

DEPLOY_STAGE=3
echo "[4/7] Starting backend (Flyway migrations run on boot)"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d backend
DEPLOY_STAGE=4
wait_for_backend_readiness

DEPLOY_STAGE=5
echo "[5/7] Starting web (SSR) and edge"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d web edge
wait_for_edge_readiness

echo "[6/7] Post-deployment smoke tests on $LOCAL_URL as $PUBLIC_HOST"
BASE_URL="$LOCAL_URL" PUBLIC_HOST="$PUBLIC_HOST" \
  SMOKE_EMAIL="$(dotenv_get SMOKE_EMAIL)" \
  SMOKE_PASSWORD="$(dotenv_get SMOKE_PASSWORD)" \
  SMOKE_ROLE_ENDPOINT="$(dotenv_get SMOKE_ROLE_ENDPOINT)" \
  SMOKE_TICKET_ID="$(dotenv_get SMOKE_TICKET_ID)" \
  SMOKE_TICKET_TOKEN="$(dotenv_get SMOKE_TICKET_TOKEN)" \
  SMOKE_TICKET_PDF="$(dotenv_get SMOKE_TICKET_PDF)" \
  SMOKE_ENTERPRISE="$(dotenv_get SMOKE_ENTERPRISE)" \
  "$ROOT/infra/smoke/production-smoke.sh"

if [[ -n "$PREVIOUS_TAG" && "$PREVIOUS_TAG" != "$TAG" ]]; then echo "$PREVIOUS_TAG" > "$PREVIOUS"; fi
echo "$TAG" > "$CURRENT"
trap - ERR

echo "[7/7] Release accepted"
"${COMPOSE[@]}" ps
