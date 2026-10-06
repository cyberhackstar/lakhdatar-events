#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${BASE_URL:?BASE_URL is required}"
: "${EVENT_ID:?EVENT_ID is required}"
: "${TICKET_TYPE_ID:?TICKET_TYPE_ID is required}"
: "${STAFF_BEARER:?STAFF_BEARER is required}"
: "${CHECKIN_QR_TOKENS:?CHECKIN_QR_TOKENS is required}"
: "${TICKET_ID:?TICKET_ID is required}"
: "${TICKET_TOKEN:?TICKET_TOKEN is required}"
: "${ADMIN_BEARER:?ADMIN_BEARER is required}"
: "${ADMIN_EVENT_ID:?ADMIN_EVENT_ID is required}"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${LOADTEST_EVENT_ID:?LOADTEST_EVENT_ID is required}"

HOSTNAME="$(python3 -c 'from urllib.parse import urlparse; import os; print(urlparse(os.environ["BASE_URL"]).hostname or "")')"
case "$HOSTNAME" in
  events.neelastack.com|www.events.neelastack.com)
    echo "Refusing enterprise staging gate against the live events.neelastack.com site." >&2
    exit 2
    ;;
esac

if [[ "${ALLOW_PRODUCTION_CHECKOUT_LOAD:-false}" == "true" ]]; then
  echo "ALLOW_PRODUCTION_CHECKOUT_LOAD must remain false for enterprise qualification." >&2
  exit 2
fi

export ENABLE_CHECKOUT_LOAD=true
export ALLOW_PRODUCTION_CHECKOUT_LOAD=false
export ENABLE_ENTERPRISE_CHECKOUT_LOAD=true
export CHECKOUT_TARGET_RATE="${CHECKOUT_TARGET_RATE:-100}"

if ! [[ "$CHECKOUT_TARGET_RATE" =~ ^[1-9][0-9]*$ ]]; then
  echo "CHECKOUT_TARGET_RATE must be a positive integer" >&2
  exit 2
fi
export THOUSANDS_MAX_VUS="${THOUSANDS_MAX_VUS:-1000}"
export THOUSANDS_RAMP="${THOUSANDS_RAMP:-2m}"
export THOUSANDS_HOLD="${THOUSANDS_HOLD:-3m}"

# The standard suite intentionally skips scenarios whose credentials are absent. This gate fails
# closed instead, then runs the full critical-path suite and database invariants.
"$SCRIPT_DIR/run-suite.sh"
"$SCRIPT_DIR/run.sh" thousands.js
BASE_URL="$BASE_URL" LOADTEST_EVENT_ID="$LOADTEST_EVENT_ID" DATABASE_URL="$DATABASE_URL" "$SCRIPT_DIR/verify-invariants.sh"

echo "ENTERPRISE_LOAD_GATE_PASSED"
