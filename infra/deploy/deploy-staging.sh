#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXPECTED_ROOT="/home/ubuntu/apps/lakhdatar-events-staging"
if [[ "$ROOT" != "$EXPECTED_ROOT" && "${DEPLOY_ALLOW_ANY_ROOT:-}" != "1" ]]; then
  echo "Refusing to deploy staging from $ROOT. Staging lives in $EXPECTED_ROOT." >&2
  exit 2
fi

dotenv_get() {
  local key="$1" line value
  line="$(grep -E "^${key}=" "$ROOT/.env" | tail -n 1 | tr -d "\r" || true)"
  [[ -n "$line" ]] || return 0
  value="${line#*=}"
  if [[ "$value" == \"* && "$value" == *\" ]]; then value="${value:1:${#value}-2}";
  elif [[ "$value" == \'* && "$value" == *\' ]]; then value="${value:1:${#value}-2}"; fi
  printf '%s' "$value"
}

TAG="${1:-}"
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "Usage: $0 <immutable-image-tag>" >&2; exit 2; }
[[ -f "$ROOT/.env" ]] || { echo "Missing $ROOT/.env" >&2; exit 2; }

PUBLIC_HOST="${PUBLIC_HOST:-$(dotenv_get PUBLIC_HOST)}"
PUBLIC_HOST="${PUBLIC_HOST:-staging-events.neelastack.com}"
[[ "$PUBLIC_HOST" == "staging-events.neelastack.com" ]] || { echo "Staging PUBLIC_HOST must be staging-events.neelastack.com" >&2; exit 2; }
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-$(dotenv_get IMAGE_NAMESPACE)}"
[[ -n "$IMAGE_NAMESPACE" ]] || { echo "IMAGE_NAMESPACE is required" >&2; exit 2; }
[[ "$IMAGE_NAMESPACE" =~ ^ghcr\.io/[A-Za-z0-9_.-]+$ ]] || { echo "IMAGE_NAMESPACE must be a GHCR owner namespace such as ghcr.io/your-org" >&2; exit 2; }
[[ "$IMAGE_NAMESPACE" != ghcr.io/your-github-owner ]] || { echo "IMAGE_NAMESPACE is still the example placeholder" >&2; exit 2; }

for required_pair in \
  "APP_ENV|staging" \
  "PUBLIC_BASE_URL|https://staging-events.neelastack.com" \
  "CORS_ALLOWED_ORIGINS|https://staging-events.neelastack.com"; do
  key="${required_pair%%|*}"; expected="${required_pair#*|}"; actual="$(dotenv_get "$key")"
  [[ "$actual" == "$expected" ]] || { echo "$key must be $expected in staging" >&2; exit 2; }
done

if grep -Eq '(^|=)https://events\.neelastack\.com($|[/,])' "$ROOT/.env"; then
  echo "Refusing staging deployment with production public origin in .env" >&2
  exit 2
fi
secret_value() { grep -E "^$1=" "$ROOT/.env" | tail -n1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//"; }
require_secret() {
  local key="$1" min="$2" value
  value="$(secret_value "$key")"
  if [[ -z "$value" || "$value" == '<'* || "$value" == replace-with-* || "$value" == change-me* || "${#value}" -lt "$min" ]]; then
    echo "$key is missing or still a placeholder/undersized value in staging .env" >&2
    exit 2
  fi
}
require_secret DB_PASSWORD 12
require_secret REDIS_PASSWORD 32
require_secret JWT_SECRET 32
require_secret TICKET_VIEW_SECRET 32
require_secret QR_SIGNING_SECRET 32
require_secret MFA_ENCRYPTION_KEY 32

provider_ok=0
cashfree_id="$(secret_value CASHFREE_APP_ID)"
cashfree_secret="$(secret_value CASHFREE_SECRET_KEY)"
cashfree_url="$(secret_value CASHFREE_BASE_URL)"
if [[ -n "$cashfree_id" || -n "$cashfree_secret" ]]; then
  [[ -n "$cashfree_id" && -n "$cashfree_secret" && -n "$cashfree_url" && "$cashfree_id" != '<'* && "$cashfree_secret" != '<'* && "$cashfree_url" != 'https://api.cashfree.com/pg' ]] || { echo 'Staging Cashfree credentials are incomplete or production endpoint is configured' >&2; exit 2; }
  provider_ok=1
fi
razor_key="$(secret_value RAZORPAY_KEY_ID)"
razor_secret="$(secret_value RAZORPAY_KEY_SECRET)"
razor_webhook="$(secret_value RAZORPAY_WEBHOOK_SECRET)"
if [[ -n "$razor_key" || -n "$razor_secret" || -n "$razor_webhook" ]]; then
  [[ "$razor_key" == rzp_test_* && -n "$razor_secret" && -n "$razor_webhook" && "$razor_secret" != '<'* && "$razor_webhook" != '<'* ]] || { echo 'Staging Razorpay credentials must be a complete test-key set' >&2; exit 2; }
  provider_ok=1
fi
[[ "$provider_ok" == 1 ]] || { echo 'At least one staging payment sandbox credential set is required' >&2; exit 2; }
if grep -Eq '^PRODUCTION_TOPOLOGY=enterprise-ha$' "$ROOT/.env"; then
  echo "Staging environment cannot be tagged as enterprise-ha production." >&2
  exit 2
fi

COMPOSE=(docker compose --env-file "$ROOT/.env" -f "$ROOT/infra/docker-compose.staging.yml")
CURRENT="$ROOT/.deploy-current"
PREVIOUS="$ROOT/.deploy-previous"
PREVIOUS_TAG="$(cat "$CURRENT" 2>/dev/null || true)"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" config >/dev/null

BACKEND_TIMEOUT="${BACKEND_READINESS_TIMEOUT_SECONDS:-300}"
EDGE_TIMEOUT="${WEB_READINESS_TIMEOUT_SECONDS:-180}"
[[ "$BACKEND_TIMEOUT" =~ ^[1-9][0-9]*$ && "$EDGE_TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo "Readiness timeouts must be positive integers" >&2; exit 2; }

rollback_previous() {
  if [[ -n "$PREVIOUS_TAG" && "$PREVIOUS_TAG" != "$TAG" && "${DEPLOY_STAGE:-0}" -lt 4 ]]; then
    echo "Staging failed before application startup; restoring $PREVIOUS_TAG" >&2
    IMAGE_TAG="$PREVIOUS_TAG" "${COMPOSE[@]}" up -d backend web edge || true
    echo "$PREVIOUS_TAG" > "$CURRENT"
  else
    echo "Staging deployment failed. Inspect staging logs; automatic DB/schema rollback is intentionally disabled." >&2
  fi
}
trap rollback_previous ERR

wait_backend() {
  local deadline=$((SECONDS + BACKEND_TIMEOUT)) state response attempt=0
  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    state="$(docker inspect --format '{{.State.Status}}' lakhdatar-staging-backend 2>/dev/null || true)"
    if [[ "$state" == "exited" || "$state" == "dead" ]]; then
      docker logs --tail=250 lakhdatar-staging-backend >&2 || true
      return 1
    fi
    if response="$(docker exec lakhdatar-staging-backend wget -qO- --timeout=5 http://127.0.0.1:8081/actuator/health/readiness 2>/dev/null)"; then
      if [[ "$response" == *'"status":"UP"'* ]]; then return 0; fi
    fi
    sleep 3
  done
  docker logs --tail=250 lakhdatar-staging-backend >&2 || true
  return 1
}

wait_edge() {
  local deadline=$((SECONDS + EDGE_TIMEOUT)) state attempt=0
  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    state="$(docker inspect --format '{{.State.Status}}' lakhdatar-staging-edge 2>/dev/null || true)"
    if [[ "$state" == "restarting" || "$state" == "exited" || "$state" == "dead" ]]; then
      docker logs --tail=250 lakhdatar-staging-edge >&2 || true
      return 1
    fi
    if [[ "$state" == "running" ]] && curl -fsS --max-time 10 http://127.0.0.1:4003/edge-health >/dev/null 2>&1; then return 0; fi
    sleep 3
  done
  docker logs --tail=250 lakhdatar-staging-edge >&2 || true
  return 1
}

echo "[1/5] Pulling immutable release $TAG"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" pull postgres redis backend web edge

echo "[2/5] Starting PostgreSQL + Redis"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d postgres redis

DEPLOY_STAGE=3
echo "[3/5] Starting backend and waiting for readiness"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d backend
wait_backend

DEPLOY_STAGE=4
echo "[4/5] Starting SSR web + staging edge"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d web edge
wait_edge

echo "[5/5] Running staging smoke tests"
BASE_URL="http://127.0.0.1:4003" PUBLIC_HOST="$PUBLIC_HOST" \
  "$ROOT/infra/smoke/staging-smoke.sh"

echo "$TAG" > "$CURRENT"
trap - ERR
"${COMPOSE[@]}" ps
