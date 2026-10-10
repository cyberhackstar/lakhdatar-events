#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DIR="${REPORT_DIR:-$PROJECT_ROOT/loadtest-results}"
mkdir -p "$REPORT_DIR"
PREFLIGHT_REPORT="$REPORT_DIR/enterprise-gate-preflight.txt"

required_vars=(
  BASE_URL EVENT_ID TICKET_TYPE_ID STAFF_BEARER CHECKIN_QR_TOKENS
  TICKET_ID TICKET_TOKEN ADMIN_BEARER ADMIN_EVENT_ID LOADTEST_EVENT_ID
)
missing_vars=()
for name in "${required_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then missing_vars+=("$name"); fi
done
{
  echo 'Neelastack Enterprise Load Gate Preflight'
  echo 'Secrets and credential values are deliberately not recorded.'
  if ((${#missing_vars[@]})); then
    echo 'status=BLOCKED'
    printf 'missing_runtime_inputs=%s\n' "${missing_vars[*]}"
  else
    echo 'status=PASS'
    echo 'missing_runtime_inputs=none'
  fi
} > "$PREFLIGHT_REPORT"
if ((${#missing_vars[@]})); then
  printf 'Enterprise load gate blocked; missing required runtime inputs: %s\n' "${missing_vars[*]}" >&2
  echo 'The automatic fixture runner did not provide generated fields; inspect infra/loadtest/auto-runner.cjs and workflow wiring.' >&2
  echo 'Do not add per-fixture LOADTEST_* secrets. See infra/loadtest/CONFIGURATION.md.' >&2
  exit 2
fi

[[ "$BASE_URL" == 'https://staging-events.neelastack.com' ]] || { echo 'Enterprise load gate must target https://staging-events.neelastack.com' >&2; exit 2; }
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

# The standard suite intentionally skips scenarios whose credentials are absent. The runner
# provisions the full generated set and this gate fails closed before running any critical scenario.
bash "$SCRIPT_DIR/run-suite.sh"
bash "$SCRIPT_DIR/run.sh" thousands.js
echo "ENTERPRISE_LOAD_SCENARIOS_PASSED"
