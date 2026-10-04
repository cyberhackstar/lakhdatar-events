#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE="${K6_IMAGE:-grafana/k6:2.2.0}"
TEST="${1:-catalog.js}"
if [ "$TEST" = "suite" ]; then exec "$SCRIPT_DIR/run-suite.sh"; fi
[ -f "$SCRIPT_DIR/$TEST" ] || { echo "Unknown test: $TEST" >&2; exit 2; }

docker run --rm -i --net=host \
  -e BASE_URL="${BASE_URL:-http://127.0.0.1:4002}" \
  -e EVENT_SLUG="${EVENT_SLUG:-}" \
  -e TICKET_ID="${TICKET_ID:-}" \
  -e TICKET_TOKEN="${TICKET_TOKEN:-}" \
  -e ADMIN_BEARER="${ADMIN_BEARER:-}" \
  -e ADMIN_EVENT_ID="${ADMIN_EVENT_ID:-}" \
  -e EVENT_ID="${EVENT_ID:-}" \
  -e TICKET_TYPE_ID="${TICKET_TYPE_ID:-}" \
  -e ENABLE_CHECKOUT_LOAD="${ENABLE_CHECKOUT_LOAD:-false}" \
  -e ALLOW_PRODUCTION_CHECKOUT_LOAD="${ALLOW_PRODUCTION_CHECKOUT_LOAD:-false}" \
  -e STAFF_BEARER="${STAFF_BEARER:-}" \
  -e GATE="${GATE:-Gate 1}" \
  -e CHECKIN_QR_TOKENS="${CHECKIN_QR_TOKENS:-}" \
  -e CHECKIN_RATE="${CHECKIN_RATE:-2}" -e CHECKIN_DURATION="${CHECKIN_DURATION:-2m}" \
  -e TEST_IDEMPOTENCY_KEY="${TEST_IDEMPOTENCY_KEY:-}" \
  -e BURST_START_RATE="${BURST_START_RATE:-50}" -e BURST_RATE_1="${BURST_RATE_1:-100}" -e BURST_RATE_2="${BURST_RATE_2:-200}" -e BURST_RATE_3="${BURST_RATE_3:-250}" \
  -e CHECKIN_RATE="${CHECKIN_RATE:-2}" -e CHECKIN_DURATION="${CHECKIN_DURATION:-2m}" \
  "$IMAGE" run - < "$SCRIPT_DIR/$TEST"
