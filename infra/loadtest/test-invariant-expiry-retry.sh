#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT
mkdir -p "$TMP/bin"

# The verifier only needs ssh-keygen to validate a private key before launching SSH. These
# controlled stubs exercise its retry policy without connecting to any VM or database.
cat > "$TMP/bin/ssh-keygen" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
cat > "$TMP/bin/ssh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
cat >/dev/null
count="$(cat "$MOCK_SSH_COUNTER" 2>/dev/null || echo 0)"
count=$((count + 1))
printf '%s\n' "$count" > "$MOCK_SSH_COUNTER"
if [[ "$MOCK_SSH_MODE" == transient && "$count" -lt 3 ]]; then
  echo 'ERROR: EXPIRED_HELD_RESERVATIONS_REMAIN rows=2' >&2
  exit 1
fi
if [[ "$MOCK_SSH_MODE" == persistent ]]; then
  echo 'ERROR: INVENTORY_INVARIANT_BROKEN rows=1' >&2
  exit 1
fi
echo 'NOTICE: LOADTEST_INVARIANTS_OK event=123e4567-e89b-12d3-a456-426614174000'
STUB
chmod +x "$TMP/bin/ssh-keygen" "$TMP/bin/ssh"

COMMON_ENV=(
  "PATH=$TMP/bin:$PATH"
  "LOADTEST_EVENT_ID=123e4567-e89b-12d3-a456-426614174000"
  "STAGING_DEPLOY_HOST=staging-test.invalid"
  "STAGING_DEPLOY_SSH_KEY=test-key-material"
  "STAGING_DEPLOY_KNOWN_HOSTS=staging-test.invalid ssh-ed25519 test-host-key"
  "MOCK_SSH_COUNTER=$TMP/counter"
)

# A transient expiry-worker race should retry and pass after the sweep has time to run.
echo 0 > "$TMP/counter"
env "${COMMON_ENV[@]}" MOCK_SSH_MODE=transient bash "$ROOT/infra/loadtest/verify-invariants.sh" > "$TMP/transient.log" 2>&1
test "$(cat "$TMP/counter")" = 3
grep -q 'LOADTEST_REMOTE_DATABASE_INVARIANTS_PASSED' "$TMP/transient.log"

# Any other invariant failure must fail immediately rather than being retried or ignored.
echo 0 > "$TMP/counter"
if env "${COMMON_ENV[@]}" MOCK_SSH_MODE=persistent bash "$ROOT/infra/loadtest/verify-invariants.sh" > "$TMP/persistent.log" 2>&1; then
  echo 'Non-expiry invariant failure was incorrectly accepted.' >&2
  exit 1
fi
test "$(cat "$TMP/counter")" = 1
grep -q 'INVENTORY_INVARIANT_BROKEN' "$TMP/persistent.log"
echo 'Reservation-expiry retry regression tests passed.'
