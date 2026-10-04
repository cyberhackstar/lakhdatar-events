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
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-$(dotenv_get IMAGE_NAMESPACE)}"
[[ -n "${IMAGE_NAMESPACE:-}" ]] || { echo "IMAGE_NAMESPACE is required (e.g. ghcr.io/owner)" >&2; exit 2; }
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "Release image tag must be a 40-character Git SHA" >&2; exit 2; }

for setting in BACKEND_READINESS_TIMEOUT_SECONDS BACKEND_READINESS_INTERVAL_SECONDS WEB_READINESS_TIMEOUT_SECONDS WEB_READINESS_INTERVAL_SECONDS; do
  value="${!setting}"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || { echo "$setting must be a positive integer" >&2; exit 2; }
done

# Validate the exact production Compose model before touching services.
IMAGE_TAG="$TAG" docker compose --env-file "$ROOT/.env" -f "$ROOT/infra/docker-compose.prod.yml" config >/dev/null

PREVIOUS_TAG="$(cat "$CURRENT" 2>/dev/null || true)"
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
"$ROOT/infra/backup/backup-postgres.sh" --allow-missing
if ls "$ROOT"/infra/backups/*.dump >/dev/null 2>&1 || [[ -n "${BACKUP_DIR:-}" && -d "${BACKUP_DIR}" && -n "$(find "${BACKUP_DIR}" -maxdepth 1 -name 'lakhdatar-*.dump' -print -quit 2>/dev/null)" ]]; then
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
smoke() {
  local url="$1"
  local body headers status
  body="$(mktemp)"
  headers="$(mktemp)"
  status="$(curl -sS --max-time 15 -D "$headers" -o "$body" -w '%{http_code}' \
    -H "Host: $PUBLIC_HOST" \
    -H 'X-Forwarded-Proto: https' \
    "$url" || true)"
  case "$status" in
    2??)
      rm -f "$body" "$headers"
      ;;
    *)
      echo "Smoke test failed: $url (Host: $PUBLIC_HOST, HTTP ${status:-unknown})" >&2
      echo "--- response headers ---" >&2
      cat "$headers" >&2 || true
      echo "--- response body (first 2000 bytes) ---" >&2
      head -c 2000 "$body" >&2 || true
      rm -f "$body" "$headers"
      return 1
      ;;
  esac
}
smoke "$LOCAL_URL/edge-health"
smoke "$LOCAL_URL/"                                   # SSR home under the canonical public host
smoke "$LOCAL_URL/api/v1/public/events/upcoming"      # catalogue API through the edge
smoke "$LOCAL_URL/robots.txt"
smoke "$LOCAL_URL/sitemap.xml"

if [[ -n "$PREVIOUS_TAG" && "$PREVIOUS_TAG" != "$TAG" ]]; then echo "$PREVIOUS_TAG" > "$PREVIOUS"; fi
echo "$TAG" > "$CURRENT"
trap - ERR

echo "[7/7] Release accepted"
"${COMPOSE[@]}" ps
