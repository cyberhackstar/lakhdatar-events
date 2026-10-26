#!/usr/bin/env bash
# Attach an existing real recovery/PITR/alerting report to an explicit human review.
# This does NOT run the drill and must not be used to override a failed payment-chaos or finance check.
# Example:
# CONTROL_ID=SINGLE_NODE_RECOVERY EVIDENCE_FILE=./recovery-report.txt \
# RELEASE_SHA=<exact-qualified-release-sha> RESULT_SCOPE='Existing staging single-node restore drill; excludes replacement VM and DNS/Tunnel failover.' \
# RESULT_RPO_SECONDS=120 RESULT_RTO_SECONDS=420 OPERATOR='Operator Name' \
# INDEPENDENT_REVIEWER='Second Reviewer' REVIEW_STATUS=PASS bash infra/readiness/record-reviewed-evidence.sh
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

: "${CONTROL_ID:?CONTROL_ID must be SINGLE_NODE_RECOVERY, PITR, or ALERTING}"
: "${EVIDENCE_FILE:?EVIDENCE_FILE must point to a real, non-empty drill/review report}"
: "${RELEASE_SHA:?RELEASE_SHA is required to bind this record to the qualified release}"
: "${RESULT_SCOPE:?RESULT_SCOPE must describe what was actually tested and what it excludes}"
: "${OPERATOR:?OPERATOR (the person preparing this record) is required}"
: "${INDEPENDENT_REVIEWER:?INDEPENDENT_REVIEWER is required and must differ from OPERATOR}"
: "${REVIEW_STATUS:?REVIEW_STATUS must be PASS or FAIL after the evidence was actually reviewed}"

[[ "$CONTROL_ID" == "SINGLE_NODE_RECOVERY" || "$CONTROL_ID" == "PITR" || "$CONTROL_ID" == "ALERTING" ]] || {
  echo "Unsupported CONTROL_ID; this wrapper cannot create payment-chaos, finance, privacy, MFA, or penetration-test evidence." >&2
  exit 2
}
[[ -f "$EVIDENCE_FILE" && -s "$EVIDENCE_FILE" ]] || { echo "EVIDENCE_FILE is missing or empty." >&2; exit 2; }
[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "RELEASE_SHA must be the exact 40-character qualified application SHA." >&2; exit 2; }
(( ${#RESULT_SCOPE} >= 30 )) || { echo "RESULT_SCOPE must have at least 30 characters and state the test scope/limitations." >&2; exit 2; }
[[ "${REVIEW_STATUS^^}" == PASS || "${REVIEW_STATUS^^}" == FAIL ]] || { echo "REVIEW_STATUS must be PASS or FAIL." >&2; exit 2; }
[[ "${INDEPENDENT_REVIEWER,,}" != "${OPERATOR,,}" ]] || { echo "INDEPENDENT_REVIEWER must be a different person from OPERATOR." >&2; exit 2; }

case "$CONTROL_ID" in
  SINGLE_NODE_RECOVERY)
    : "${RESULT_RPO_SECONDS:?Supply the measured RPO from the reviewed recovery exercise; do not use the dump-restore candidate by default}"
    : "${RESULT_RTO_SECONDS:?Supply the measured RTO from the reviewed recovery exercise}"
    [[ "$RESULT_RPO_SECONDS" =~ ^[0-9]{1,9}$ && "$RESULT_RTO_SECONDS" =~ ^[0-9]{1,9}$ ]] || {
      echo "RESULT_RPO_SECONDS and RESULT_RTO_SECONDS must be measured non-negative integer seconds." >&2; exit 2;
    }
    ITEM="single-node-recovery-reviewed"
    TITLE="Human-reviewed single-node recovery evidence"
    ;;
  PITR)
    ITEM="pitr-reviewed"
    TITLE="Human-reviewed point-in-time recovery evidence"
    ;;
  ALERTING)
    ITEM="alerting-reviewed"
    TITLE="Human-reviewed alerting evidence"
    ;;
esac

begin_report "$ITEM" "$CONTROL_ID" "$TITLE"
SOURCE_BASENAME="$(basename -- "$EVIDENCE_FILE" | tr -c 'A-Za-z0-9._-' '_')"
SOURCE_COPY="${CONTROL_ID,,}-source-$(stamp)-${SOURCE_BASENAME}"
[[ ! -e "$EVIDENCE_DIR/$SOURCE_COPY" ]] || { echo "Refusing to overwrite an existing evidence file." >&2; exit 2; }
cp -- "$EVIDENCE_FILE" "$EVIDENCE_DIR/$SOURCE_COPY"
chmod 600 "$EVIDENCE_DIR/$SOURCE_COPY"
SOURCE_SHA="$(sha256sum "$EVIDENCE_DIR/$SOURCE_COPY" | awk '{print $1}')"
say "Reviewed source file: $SOURCE_COPY"
say "Reviewed source SHA-256: $SOURCE_SHA"
say "Documented measurement scope: $RESULT_SCOPE"
say "Independent reviewer recorded: $INDEPENDENT_REVIEWER"
if [[ "$CONTROL_ID" == SINGLE_NODE_RECOVERY ]]; then
  say "Measured recovery point objective (seconds): $RESULT_RPO_SECONDS"
  say "Measured recovery time objective (seconds): $RESULT_RTO_SECONDS"
  export RESULT_RPO_SECONDS RESULT_RTO_SECONDS
fi
if [[ "${REVIEW_STATUS^^}" == PASS ]]; then
  pass "Named reviewers accepted the attached recovery evidence and stated scope"
else
  fail "Reviewer marked the attached evidence as failed or not accepted"
fi
# The immutable report bundle includes the original report. finish_report writes PASS or FAIL JSON
# and checksums before returning the corresponding status.
finish_report "$SOURCE_COPY"
