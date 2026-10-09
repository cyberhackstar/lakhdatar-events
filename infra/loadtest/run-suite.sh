#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DIR="${REPORT_DIR:-$PROJECT_ROOT/loadtest-results}"
mkdir -p "$REPORT_DIR"
IMAGE="${K6_IMAGE:-grafana/k6:2.2.0@sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6}"
BASE_URL="${BASE_URL:?Set BASE_URL to staging/load-test host}"
run(){ local name="$1"; echo "=== k6: $name ==="; docker run --rm -i --net=host -v "$REPORT_DIR:/results" \
  -e BASE_URL="$BASE_URL" \
  -e EVENT_SLUG="${EVENT_SLUG:-}" -e TICKET_ID="${TICKET_ID:-}" -e TICKET_TOKEN="${TICKET_TOKEN:-}" \
  -e ADMIN_BEARER="${ADMIN_BEARER:-}" -e ADMIN_EVENT_ID="${ADMIN_EVENT_ID:-}" \
  -e EVENT_ID="${EVENT_ID:-}" -e TICKET_TYPE_ID="${TICKET_TYPE_ID:-}" \
  -e ENABLE_CHECKOUT_LOAD="${ENABLE_CHECKOUT_LOAD:-false}" -e ENABLE_ENTERPRISE_CHECKOUT_LOAD="${ENABLE_ENTERPRISE_CHECKOUT_LOAD:-false}" -e CHECKOUT_TARGET_RATE="${CHECKOUT_TARGET_RATE:-100}" -e ALLOW_PRODUCTION_CHECKOUT_LOAD="${ALLOW_PRODUCTION_CHECKOUT_LOAD:-false}" \
  -e STAFF_BEARER="${STAFF_BEARER:-}" -e GATE="${GATE:-Gate 1}" -e CHECKIN_QR_TOKENS="${CHECKIN_QR_TOKENS:-}" \
  -e RATE="${RATE:-50}" -e DURATION="${DURATION:-2m}" -e VUS="${VUS:-20}" -e MAX_VUS="${MAX_VUS:-100}" \
  -e TEST_IDEMPOTENCY_KEY="${TEST_IDEMPOTENCY_KEY:-}" \
  -e CHECKIN_RATE="${CHECKIN_RATE:-2}" -e CHECKIN_DURATION="${CHECKIN_DURATION:-2m}" \
  -e BURST_START_RATE="${BURST_START_RATE:-50}" -e BURST_RATE_1="${BURST_RATE_1:-100}" -e BURST_RATE_2="${BURST_RATE_2:-200}" -e BURST_RATE_3="${BURST_RATE_3:-250}" \
  -e THOUSANDS_MAX_VUS="${THOUSANDS_MAX_VUS:-1000}" -e THOUSANDS_RAMP="${THOUSANDS_RAMP:-2m}" -e THOUSANDS_HOLD="${THOUSANDS_HOLD:-3m}" \
  "$IMAGE" run --summary-export="/results/${name%.js}.json" - < "$SCRIPT_DIR/$name"; }

run catalog.js
run burst.js
run public-event.js
run seo.js
[ -n "${EVENT_ID:-}" ] && [ -n "${TICKET_TYPE_ID:-}" ] && run checkout-idempotency.js
[ "${ENABLE_CHECKOUT_LOAD:-false}" = true ] && run checkout.js
[ "${ENABLE_ENTERPRISE_CHECKOUT_LOAD:-false}" = true ] && run checkout-hot-sale.js
[ -n "${CHECKIN_QR_TOKENS:-}" ] && run checkin.js
[ -n "${TICKET_ID:-}" ] && [ -n "${TICKET_TOKEN:-}" ] && run ticket-pdf.js
[ -n "${ADMIN_BEARER:-}" ] && [ -n "${ADMIN_EVENT_ID:-}" ] && run operations.js
