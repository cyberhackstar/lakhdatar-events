#!/usr/bin/env bash
set -Eeuo pipefail
APP_DIR="${HA_APP_DIR:-/home/ubuntu/apps/lakhdatar-events-ha}"
cd "$APP_DIR"
[[ -f .deploy-previous ]] || { echo 'No previous HA release recorded' >&2; exit 2; }
PREVIOUS="$(cat .deploy-previous)"
[[ "$PREVIOUS" =~ ^[0-9a-f]{40}$ ]] || { echo 'Stored previous HA release is invalid' >&2; exit 2; }
[[ -f .env && -f docker-compose.ha.yml ]] || { echo 'HA deployment files are incomplete' >&2; exit 2; }
grep -Eq '^PRODUCTION_TOPOLOGY=enterprise-ha$' .env || { echo 'HA rollback requires PRODUCTION_TOPOLOGY=enterprise-ha' >&2; exit 2; }
grep -Eq '^MFA_REQUIRED_FOR_PRIVILEGED=true$' .env || { echo 'HA rollback requires privileged MFA' >&2; exit 2; }
grep -Eq '^REDIS_SSL=true$' .env || { echo 'HA rollback requires Redis TLS' >&2; exit 2; }
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$PREVIOUS/" .env
COMPOSE=(docker compose --env-file .env -f docker-compose.ha.yml)
"${COMPOSE[@]}" config >/dev/null
"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up -d --remove-orphans
for _ in $(seq 1 40); do
  if curl -fsS --max-time 5 http://127.0.0.1:4002/edge-health >/dev/null 2>&1; then
    printf '%s\n' "$PREVIOUS" > .deploy-current
    echo "HA node rolled back to $PREVIOUS"
    exit 0
  fi
  sleep 3
done
"${COMPOSE[@]}" ps >&2 || true
exit 1
