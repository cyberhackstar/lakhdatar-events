#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../ha" && pwd)"
APP_DIR="${HA_APP_DIR:-/home/ubuntu/apps/lakhdatar-events-ha}"
TAG="${1:-}"
[[ -n "$TAG" ]] || { echo "Usage: $0 <immutable-image-tag>" >&2; exit 2; }
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "IMAGE_TAG must be a 40-character Git SHA" >&2; exit 2; }
[[ -f "$ROOT/.env" ]] || { echo "Missing $ROOT/.env" >&2; exit 2; }
[[ "${PRODUCTION_TOPOLOGY:-}" == "enterprise-ha" ]] || { echo "PRODUCTION_TOPOLOGY must be enterprise-ha" >&2; exit 2; }
[[ "${ALLOW_SINGLE_NODE_PRODUCTION:-false}" != "true" ]] || { echo "ALLOW_SINGLE_NODE_PRODUCTION is incompatible with enterprise HA" >&2; exit 2; }
require_env_value() {
  local key="$1" pattern="$2"
  grep -Eq "^${key}=${pattern}$" "$ROOT/.env" || { echo "HA environment is missing or invalid: $key" >&2; exit 2; }
}
require_env_value PRODUCTION_TOPOLOGY 'enterprise-ha'
require_env_value MFA_REQUIRED_FOR_PRIVILEGED 'true'
require_env_value REDIS_SSL 'true'
if grep -Eq '^DB_URL=.*(//|@)(localhost|127\.0\.0\.1)(:|/|$)' "$ROOT/.env" || grep -Eq '^REDIS_HOST=(localhost|127\.0\.0\.1)$' "$ROOT/.env"; then
  echo 'Enterprise HA cannot use localhost database/Redis endpoints' >&2
  exit 2
fi
if grep -Eq '^IMAGE_NAMESPACE=(ghcr\.io/your-org|your-org/|)$' "$ROOT/.env"; then
  echo 'IMAGE_NAMESPACE must point to the real private registry namespace' >&2
  exit 2
fi

mkdir -p "$APP_DIR"
cp "$ROOT/docker-compose.ha.example.yml" "$APP_DIR/docker-compose.ha.yml"
cp "$ROOT/nginx-ha.conf.example" "$APP_DIR/nginx-ha.conf.example"
cp "$ROOT/.env" "$APP_DIR/.env"
chmod 600 "$APP_DIR/.env"

cd "$APP_DIR"
CURRENT_TAG="$(cat .deploy-current 2>/dev/null || true)"

# Inject the exact SHA without sourcing dotenv content.
if grep -q '^IMAGE_TAG=' .env; then
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$TAG/" .env
else
  printf 'IMAGE_TAG=%s\n' "$TAG" >> .env
fi

COMPOSE=(docker compose --env-file .env -f docker-compose.ha.yml)
"${COMPOSE[@]}" config >/dev/null
"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up -d --remove-orphans

for _ in $(seq 1 40); do
  if curl -fsS --max-time 5 http://127.0.0.1:4002/edge-health >/dev/null 2>&1; then
    [[ -n "$CURRENT_TAG" ]] && printf '%s\n' "$CURRENT_TAG" > .deploy-previous
    printf '%s\n' "$TAG" > .deploy-current
    echo "HA node deployed and edge-health is healthy: $TAG"
    exit 0
  fi
  sleep 3
done

echo "HA node did not become healthy" >&2
"${COMPOSE[@]}" ps >&2 || true
exit 1
