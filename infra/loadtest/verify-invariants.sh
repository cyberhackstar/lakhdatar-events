#!/usr/bin/env bash
set -euo pipefail
: "${LOADTEST_EVENT_ID:?LOADTEST_EVENT_ID is required}"
[[ "$LOADTEST_EVENT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]] || { echo 'LOADTEST_EVENT_ID must be a UUID' >&2; exit 2; }
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Invariant verification runs immediately after a timed load profile. Reservations that cross
# their configured hold-expiry boundary in the last seconds of the run may still be waiting for
# the scheduled expiry worker (default sweep interval 15s). Retry only that exact transient
# invariant with a bounded deadline; all inventory/payment/ticket invariant failures remain hard.
TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT
umask 077
MAX_VERIFY_ATTEMPTS=15
VERIFY_RETRY_DELAY_SECONDS=2

run_with_expiry_retry() {
  local attempt=1
  while :; do
    if "$@" >"$TMP_DIR/verify.out" 2>&1; then
      cat "$TMP_DIR/verify.out"
      return 0
    fi
    if grep -q 'EXPIRED_HELD_RESERVATIONS_REMAIN' "$TMP_DIR/verify.out" && (( attempt < MAX_VERIFY_ATTEMPTS )); then
      echo "Reservation invariant found expired HELD rows; waiting for the scheduled expiry worker (attempt ${attempt}/${MAX_VERIFY_ATTEMPTS})." >&2
      sleep "$VERIFY_RETRY_DELAY_SECONDS"
      attempt=$((attempt + 1))
      continue
    fi
    cat "$TMP_DIR/verify.out" >&2
    return 1
  done
}

run_local_check() {
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v event_public_id="$LOADTEST_EVENT_ID" -f "$SCRIPT_DIR/verify-invariants.sql"
}

# Local/dev compatibility: a DB URL can still be used locally. GitHub workflows deliberately do
# not provide one. Staging uses the existing protected deploy SSH material and invokes psql inside
# the isolated staging PostgreSQL container; the deployment .env and DB password never leave VM.
if [[ -n "${DATABASE_URL:-}" ]]; then
  command -v psql >/dev/null || { echo 'psql is required when DATABASE_URL is used' >&2; exit 2; }
  run_with_expiry_retry run_local_check
  exit 0
fi
: "${STAGING_DEPLOY_HOST:?STAGING_DEPLOY_HOST is required for remote read-only invariant checks}"
: "${STAGING_DEPLOY_SSH_KEY:?STAGING_DEPLOY_SSH_KEY is required for remote read-only invariant checks}"
: "${STAGING_DEPLOY_KNOWN_HOSTS:?STAGING_DEPLOY_KNOWN_HOSTS is required for pinned SSH host verification}"

[[ "$STAGING_DEPLOY_HOST" =~ ^[A-Za-z0-9._:@-]+$ ]] || { echo 'STAGING_DEPLOY_HOST contains invalid characters' >&2; exit 2; }
case "$STAGING_DEPLOY_HOST" in *@*) SSH_TARGET="$STAGING_DEPLOY_HOST";; *) SSH_TARGET="ubuntu@$STAGING_DEPLOY_HOST";; esac
printf '%s\n' "$STAGING_DEPLOY_SSH_KEY" > "$TMP_DIR/id_ed25519"
printf '%s\n' "$STAGING_DEPLOY_KNOWN_HOSTS" > "$TMP_DIR/known_hosts"
chmod 600 "$TMP_DIR/id_ed25519" "$TMP_DIR/known_hosts"
ssh-keygen -y -f "$TMP_DIR/id_ed25519" >/dev/null
REMOTE_CMD="docker exec -i lakhdatar-staging-postgres sh -lc 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -v ON_ERROR_STOP=1 -v event_public_id=$LOADTEST_EVENT_ID'"
run_remote_check() {
  ssh -i "$TMP_DIR/id_ed25519" -o BatchMode=yes -o ConnectTimeout=15 -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
    -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$TMP_DIR/known_hosts" \
    "$SSH_TARGET" "$REMOTE_CMD" < "$SCRIPT_DIR/verify-invariants.sql"
}
run_with_expiry_retry run_remote_check
echo 'LOADTEST_REMOTE_DATABASE_INVARIANTS_PASSED'
