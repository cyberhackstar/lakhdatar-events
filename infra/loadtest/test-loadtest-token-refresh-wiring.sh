#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/reports"
cat > "$TMP/bin/node" <<'MOCK_NODE'
#!/usr/bin/env bash
set -euo pipefail
case "${REFRESH_TOKEN_ROLE:-}" in
  staff) printf '%s\n' 'mock-fresh-staff-token' ;;
  admin) printf '%s\n' 'mock-fresh-admin-token' ;;
  *) echo 'unexpected token-refresh role' >&2; exit 2 ;;
esac
MOCK_NODE
cat > "$TMP/bin/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf 'CALL|%s|%s\n' "${MOCK_CONTEXT:-unknown}" "$*" >> "$MOCK_DOCKER_LOG"
MOCK_DOCKER
chmod +x "$TMP/bin/node" "$TMP/bin/docker"
export PATH="$TMP/bin:$PATH"
export MOCK_DOCKER_LOG="$TMP/docker.log"
export BASE_URL='https://staging-events.neelastack.com'
export STAFF_EMAIL='scanner@example.test' STAFF_PASSWORD='never-forward-this-staff-password'
export ADMIN_REFRESH_EMAIL='admin@example.test' ADMIN_REFRESH_PASSWORD='never-forward-this-admin-password' ADMIN_REFRESH_TOTP_SECRET='test-totp-secret'
export STAFF_BEARER='stale-staff-token' ADMIN_BEARER='stale-admin-token'
export CHECKIN_QR_TOKENS='mock-qr' EVENT_ID='event-id' TICKET_TYPE_ID='type-id' TICKET_ID='ticket-id' TICKET_TOKEN='ticket-token'
export ADMIN_EVENT_ID='event-id' ENABLE_CHECKOUT_LOAD='true' ENABLE_ENTERPRISE_CHECKOUT_LOAD='true'
export REPORT_DIR="$TMP/reports" K6_IMAGE='mock-k6-image'
export MOCK_CONTEXT='suite'
bash "$ROOT/infra/loadtest/run-suite.sh" >/dev/null

grep -q 'CALL|suite|.*STAFF_BEARER=mock-fresh-staff-token.*checkin.json' "$MOCK_DOCKER_LOG"
grep -q 'CALL|suite|.*ADMIN_BEARER=mock-fresh-admin-token.*operations.json' "$MOCK_DOCKER_LOG"
grep -q 'CALL|suite|.*PDF_RATE=15.*OPERATIONS_RATE=4.*ticket-pdf.json' "$MOCK_DOCKER_LOG"
grep -q 'CALL|suite|.*OPERATIONS_RATE=4.*operations.json' "$MOCK_DOCKER_LOG"
if grep -qE 'never-forward-this-(staff|admin)-password|STAFF_PASSWORD=|ADMIN_REFRESH_PASSWORD=|ADMIN_REFRESH_TOTP_SECRET=' "$MOCK_DOCKER_LOG"; then
  echo 'FAIL: a password/TOTP secret reached the k6 container arguments' >&2
  exit 1
fi

# Verify direct scenarios use the same refresh path (using a fake docker; no network is used).
export MOCK_CONTEXT='single-checkin'
bash "$ROOT/infra/loadtest/run.sh" checkin.js >/dev/null
grep -q 'CALL|single-checkin|.*STAFF_BEARER=mock-fresh-staff-token' "$MOCK_DOCKER_LOG"
export MOCK_CONTEXT='single-operations'
bash "$ROOT/infra/loadtest/run.sh" operations.js >/dev/null
grep -q 'CALL|single-operations|.*ADMIN_BEARER=mock-fresh-admin-token' "$MOCK_DOCKER_LOG"
if grep -qE 'never-forward-this-(staff|admin)-password|STAFF_PASSWORD=|ADMIN_REFRESH_PASSWORD=|ADMIN_REFRESH_TOTP_SECRET=' "$MOCK_DOCKER_LOG"; then
  echo 'FAIL: a password/TOTP secret reached the k6 container arguments' >&2
  exit 1
fi

echo 'PASS: suite and single-scenario runners refresh staff/admin JWTs just in time without forwarding passwords or TOTP secrets to k6'
