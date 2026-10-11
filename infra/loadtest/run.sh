#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE="${K6_IMAGE:-grafana/k6:2.2.0@sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6}"
TEST="${1:-catalog.js}"
if [ "$TEST" = "suite" ]; then exec "$SCRIPT_DIR/run-suite.sh"; fi
[ -f "$SCRIPT_DIR/$TEST" ] || { echo "Unknown test: $TEST" >&2; exit 2; }

STAFF_BEARER_VALUE="${STAFF_BEARER:-}"
ADMIN_BEARER_VALUE="${ADMIN_BEARER:-}"
if [ "$TEST" = "checkin.js" ] && [ -n "${STAFF_EMAIL:-}" ] && [ -n "${STAFF_PASSWORD:-}" ]; then
  echo "Refreshing short-lived scanner session immediately before check-in load"
  STAFF_BEARER_VALUE="$(BASE_URL="${BASE_URL:-http://127.0.0.1:4002}" REFRESH_TOKEN_ROLE=staff STAFF_EMAIL="$STAFF_EMAIL" STAFF_PASSWORD="$STAFF_PASSWORD" node "$SCRIPT_DIR/refresh-access-token.cjs")"
  [ -n "$STAFF_BEARER_VALUE" ] || { echo "Scanner session refresh returned an empty token" >&2; exit 2; }
fi
if [ "$TEST" = "operations.js" ] && [ -n "${ADMIN_REFRESH_EMAIL:-}" ] && [ -n "${ADMIN_REFRESH_PASSWORD:-}" ]; then
  echo "Refreshing short-lived admin session immediately before operations load"
  ADMIN_BEARER_VALUE="$(BASE_URL="${BASE_URL:-http://127.0.0.1:4002}" REFRESH_TOKEN_ROLE=admin ADMIN_REFRESH_EMAIL="$ADMIN_REFRESH_EMAIL" ADMIN_REFRESH_PASSWORD="$ADMIN_REFRESH_PASSWORD" ADMIN_REFRESH_TOTP_SECRET="${ADMIN_REFRESH_TOTP_SECRET:-}" node "$SCRIPT_DIR/refresh-access-token.cjs")"
  [ -n "$ADMIN_BEARER_VALUE" ] || { echo "Admin session refresh returned an empty token" >&2; exit 2; }
fi

docker run --rm -i --net=host \
  -e BASE_URL="${BASE_URL:-http://127.0.0.1:4002}" \
  -e EVENT_SLUG="${EVENT_SLUG:-}" \
  -e TICKET_ID="${TICKET_ID:-}" \
  -e TICKET_TOKEN="${TICKET_TOKEN:-}" \
  -e ADMIN_BEARER="$ADMIN_BEARER_VALUE" \
  -e ADMIN_EVENT_ID="${ADMIN_EVENT_ID:-}" \
  -e EVENT_ID="${EVENT_ID:-}" \
  -e TICKET_TYPE_ID="${TICKET_TYPE_ID:-}" \
  -e ENABLE_CHECKOUT_LOAD="${ENABLE_CHECKOUT_LOAD:-false}" \
  -e ENABLE_ENTERPRISE_CHECKOUT_LOAD="${ENABLE_ENTERPRISE_CHECKOUT_LOAD:-false}" \
  -e PDF_RATE="${PDF_RATE:-15}" -e OPERATIONS_RATE="${OPERATIONS_RATE:-4}" \
  -e CHECKOUT_TARGET_RATE="${CHECKOUT_TARGET_RATE:-100}" \
  -e ALLOW_PRODUCTION_CHECKOUT_LOAD="${ALLOW_PRODUCTION_CHECKOUT_LOAD:-false}" \
  -e STAFF_BEARER="$STAFF_BEARER_VALUE" \
  -e GATE="${GATE:-Gate 1}" \
  -e CHECKIN_QR_TOKENS="${CHECKIN_QR_TOKENS:-}" \
  -e CHECKIN_RATE="${CHECKIN_RATE:-2}" -e CHECKIN_DURATION="${CHECKIN_DURATION:-2m}" \
  -e TEST_IDEMPOTENCY_KEY="${TEST_IDEMPOTENCY_KEY:-}" \
  -e BURST_START_RATE="${BURST_START_RATE:-50}" -e BURST_RATE_1="${BURST_RATE_1:-100}" -e BURST_RATE_2="${BURST_RATE_2:-200}" -e BURST_RATE_3="${BURST_RATE_3:-250}" \
  -e THOUSANDS_MAX_VUS="${THOUSANDS_MAX_VUS:-1000}" -e THOUSANDS_RAMP="${THOUSANDS_RAMP:-2m}" -e THOUSANDS_HOLD="${THOUSANDS_HOLD:-3m}" \
  -e CHECKIN_RATE="${CHECKIN_RATE:-2}" -e CHECKIN_DURATION="${CHECKIN_DURATION:-2m}" \
  "$IMAGE" run - < "$SCRIPT_DIR/$TEST"
