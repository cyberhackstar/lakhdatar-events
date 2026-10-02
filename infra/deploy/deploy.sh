#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
fi
EXPECTED_ROOT="/home/ubuntu/apps/lakhdatar-events"
if [[ "$ROOT" != "$EXPECTED_ROOT" && "${DEPLOY_ALLOW_ANY_ROOT:-}" != "1" ]]; then
  echo "Refusing to deploy from $ROOT. Production lives in $EXPECTED_ROOT." >&2
  exit 2
fi

COMPOSE=(docker compose --env-file "$ROOT/.env" -f "$ROOT/infra/docker-compose.prod.yml")
CURRENT="$ROOT/.deploy-current"
PREVIOUS="$ROOT/.deploy-previous"
TAG="${1:-}"
LOCAL_URL="http://127.0.0.1:4002"

[[ -n "$TAG" ]] || { echo "Usage: $0 <immutable-image-tag>" >&2; exit 2; }
[[ -f "$ROOT/.env" ]] || { echo "Missing $ROOT/.env" >&2; exit 2; }
[[ -n "${IMAGE_NAMESPACE:-}" ]] || { echo "IMAGE_NAMESPACE is required (e.g. ghcr.io/owner)" >&2; exit 2; }
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "Release image tag must be a 40-character Git SHA" >&2; exit 2; }

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
for _ in {1..40}; do
  IMAGE_TAG="$TAG" "${COMPOSE[@]}" exec -T backend wget -qO- http://localhost:8080/actuator/health >/dev/null 2>&1 && break
  sleep 3
done
IMAGE_TAG="$TAG" "${COMPOSE[@]}" exec -T backend wget -qO- http://localhost:8080/actuator/health >/dev/null

DEPLOY_STAGE=5
echo "[5/7] Starting web (SSR) and edge"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d web edge
for _ in {1..30}; do curl -fsS "$LOCAL_URL/edge-health" >/dev/null 2>&1 && break; sleep 2; done

echo "[6/7] Post-deployment smoke tests on $LOCAL_URL"
smoke() { curl -fsS --max-time 15 "$1" >/dev/null; }
smoke "$LOCAL_URL/edge-health"
smoke "$LOCAL_URL/"                                   # SSR home
smoke "$LOCAL_URL/api/v1/public/events/upcoming"      # catalogue API through the edge
smoke "$LOCAL_URL/robots.txt"
smoke "$LOCAL_URL/sitemap.xml"

if [[ -n "$PREVIOUS_TAG" && "$PREVIOUS_TAG" != "$TAG" ]]; then echo "$PREVIOUS_TAG" > "$PREVIOUS"; fi
echo "$TAG" > "$CURRENT"
trap - ERR

echo "[7/7] Release accepted"
"${COMPOSE[@]}" ps
