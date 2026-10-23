#!/usr/bin/env bash
# Updated offsite backup access/retention review. Read-only to R2; does not attempt overwrite or delete.
#   R2_BUCKET=neelastack-prod-db-backups R2_ENDPOINT_URL=https://<account-id>.r2.cloudflarestorage.com \
#   R2_LOCK_TEST_KEY=production/pitr-stream/lock-validation/lock-test-20261023T031254Z-1984425.txt \
#   R2_EVIDENCE_TEST_KEY=production/readiness-evidence/rpo-canary-20261023T005204Z.txt \
#   AWS_PROFILE=neelastack-r2 OPERATOR='Name' bash infra/readiness/offsite-backup-review.sh
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "$SCRIPT_DIR/lib.sh"
: "${R2_BUCKET:?R2_BUCKET is required}"
: "${R2_ENDPOINT_URL:?R2_ENDPOINT_URL is required}"
: "${R2_LOCK_TEST_KEY:?Set the exact existing lock-validation object key; do not create/delete one here}"
R2_EVIDENCE_TEST_KEY="${R2_EVIDENCE_TEST_KEY:-production/readiness-evidence/rpo-canary-20261023T005204Z.txt}"
[[ "$R2_ENDPOINT_URL" == https://* ]] || { echo "R2_ENDPOINT_URL must use HTTPS." >&2; exit 2; }
[[ "$R2_LOCK_TEST_KEY" =~ ^production/pitr-stream/lock-validation/[A-Za-z0-9._/-]+$ && "$R2_LOCK_TEST_KEY" != *..* ]] || { echo "R2_LOCK_TEST_KEY must be an existing lock-validation object key." >&2; exit 2; }
[[ "$R2_EVIDENCE_TEST_KEY" =~ ^production/readiness-evidence/[A-Za-z0-9._/-]+$ && "$R2_EVIDENCE_TEST_KEY" != *..* ]] || { echo "R2_EVIDENCE_TEST_KEY must be an evidence object key under the protected prefix." >&2; exit 2; }
command -v aws >/dev/null 2>&1 || { echo "AWS CLI is required." >&2; exit 2; }
AWS_PROFILE="${AWS_PROFILE:-neelastack-r2}"
awsx() { aws --profile "$AWS_PROFILE" --endpoint-url "$R2_ENDPOINT_URL" "$@"; }
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
begin_report offsite-backup-review OFFSITE_BACKUP "Updated offsite backup, bucket-lock, retention and access review"
say "Bucket: $R2_BUCKET"; say "AWS profile name (no credential values recorded): $AWS_PROFILE"; say "Lock test object key: $R2_LOCK_TEST_KEY"
if awsx s3api head-object --bucket "$R2_BUCKET" --key "$R2_LOCK_TEST_KEY" >"$WORK/head.json" 2>"$WORK/head.err"; then
  pass "lock-validation object exists and is readable via metadata"
  if awsx s3 cp "s3://$R2_BUCKET/$R2_LOCK_TEST_KEY" "$WORK/lock-test-object" >/dev/null 2>&1 && [[ -s "$WORK/lock-test-object" ]]; then
    pass "lock-validation object download succeeded; sha256=$(sha256sum "$WORK/lock-test-object" | awk '{print $1}')"
  else fail "lock-validation object could not be downloaded/read back"; fi
else
  fail "lock-validation object could not be found through the configured R2 endpoint/profile"
fi
EVIDENCE_BASENAME="$(basename "$R2_EVIDENCE_TEST_KEY")"
if awsx s3 cp "s3://$R2_BUCKET/$R2_EVIDENCE_TEST_KEY" "$WORK/$EVIDENCE_BASENAME" >/dev/null 2>&1 && \
   awsx s3 cp "s3://$R2_BUCKET/$R2_EVIDENCE_TEST_KEY.sha256" "$WORK/$EVIDENCE_BASENAME.sha256" >/dev/null 2>&1; then
  if ( cd "$WORK" && sha256sum -c "$EVIDENCE_BASENAME.sha256" >/dev/null 2>&1 ); then
    pass "offsite evidence report and SHA-256 sidecar downloaded and verified: $R2_EVIDENCE_TEST_KEY"
  else
    fail "offsite evidence report checksum does not match its retained R2 sidecar: $R2_EVIDENCE_TEST_KEY"
  fi
else
  fail "could not download offsite evidence object and .sha256 sidecar: $R2_EVIDENCE_TEST_KEY"
fi
say ""; say "== Operator review checklist (verify in Cloudflare dashboard and retained test record) =="
checklist "$SCRIPT_DIR/checklists/offsite-backup.txt"
finish_report
