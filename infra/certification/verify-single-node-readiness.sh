#!/usr/bin/env bash
set -Eeuo pipefail

# Fail-closed operational readiness for a deliberately single-node production deployment.
# This output is not an enterprise-HA certification and must never satisfy the HA promotion gate.
issues=()
add_issue() { issues+=("$1"); }
require_id() {
  local name="$1" value="${!1:-}"
  # Keep the character check portable across GNU Bash and Git Bash/MSYS.
  # Some POSIX regex implementations reject repetition upper bounds above 255,
  # so enforce the length with Bash string arithmetic instead of {6,256}.
  if [[ "$value" =~ ^[A-Za-z0-9._:/-]+$ ]] && (( ${#value} >= 6 && ${#value} <= 256 )); then
    :
  else
    add_issue "$name is missing or invalid (expected a stable 6-256 character evidence identifier)"
  fi
}
require_status() {
  local name="$1" value="${!1:-}"
  [[ "$value" == "PASS" ]] || add_issue "$name must be PASS after the real exercise/review is complete"
}
require_uint() {
  local name="$1" value="${!1:-}"
  [[ "$value" =~ ^[0-9]{1,9}$ ]] || add_issue "$name is missing or invalid (expected a non-negative integer of at most 9 digits)"
}

require_id SINGLE_NODE_RECOVERY_EVIDENCE_ID
require_id OFFSITE_BACKUP_EVIDENCE_ID
require_id PITR_EVIDENCE_ID
require_id PEN_TEST_EVIDENCE_ID
require_id PAYMENT_CHAOS_EVIDENCE_ID
require_id ALERTING_EVIDENCE_ID
require_id FINANCE_RECONCILIATION_EVIDENCE_ID
require_id PRIVACY_REVIEW_EVIDENCE_ID
require_id MFA_EVIDENCE_ID
require_status SINGLE_NODE_RECOVERY_STATUS
require_status OFFSITE_BACKUP_STATUS
require_status PITR_STATUS
require_status PEN_TEST_STATUS
require_status PAYMENT_CHAOS_STATUS
require_status ALERTING_STATUS
require_status FINANCE_RECONCILIATION_STATUS
require_status PRIVACY_REVIEW_STATUS
require_status MFA_STATUS

[[ "${PRODUCTION_TOPOLOGY:-}" == "single-node" ]] || add_issue 'PRODUCTION_TOPOLOGY must be single-node for this readiness gate'
require_uint HA_NODE_COUNT
require_uint RPO_SECONDS
require_uint RTO_SECONDS
require_uint MAX_RPO_SECONDS
require_uint MAX_RTO_SECONDS
[[ "${CERTIFIED_SHA:-}" =~ ^[0-9a-f]{40}$ ]] || add_issue 'CERTIFIED_SHA must be the exact 40-character lowercase release SHA'
if [[ "${HA_NODE_COUNT:-}" =~ ^[0-9]{1,9}$ ]] && (( 10#$HA_NODE_COUNT != 1 )); then
  add_issue 'HA_NODE_COUNT must equal 1 for single-node readiness'
fi
if [[ "${RPO_SECONDS:-}" =~ ^[0-9]{1,9}$ && "${MAX_RPO_SECONDS:-}" =~ ^[0-9]{1,9}$ ]] && (( 10#$RPO_SECONDS > 10#$MAX_RPO_SECONDS )); then
  add_issue "Measured RPO (${RPO_SECONDS}s) exceeds MAX_RPO_SECONDS (${MAX_RPO_SECONDS}s)"
fi
if [[ "${RTO_SECONDS:-}" =~ ^[0-9]{1,9}$ && "${MAX_RTO_SECONDS:-}" =~ ^[0-9]{1,9}$ ]] && (( 10#$RTO_SECONDS > 10#$MAX_RTO_SECONDS )); then
  add_issue "Measured RTO (${RTO_SECONDS}s) exceeds MAX_RTO_SECONDS (${MAX_RTO_SECONDS}s)"
fi

# Remove stale output before validation, so a failed rerun cannot leave a prior PASS artifact.
if [[ -d certification-evidence ]]; then
  rm -f certification-evidence/single-node-production-readiness.json
fi
if ((${#issues[@]})); then
  echo '::error title=Single-node production readiness evidence incomplete::Configure real evidence in the protected single-node-production-readiness GitHub Environment. Do not invent evidence or mark incomplete drills PASS.' >&2
  printf '::error::%s\n' "${issues[@]}" >&2
  echo 'Documentation: docs/SINGLE-NODE-PRODUCTION-READINESS.md' >&2
  exit 2
fi

mkdir -p certification-evidence
python3 - <<'PYJSON'
import json, os
from datetime import datetime, timezone
from pathlib import Path
out = {
  'schemaVersion': 1,
  'releaseSha': os.environ['CERTIFIED_SHA'],
  'status': 'PASS',
  'readinessType': 'single-node-production-readiness',
  'certifiedAtUtc': datetime.now(timezone.utc).isoformat(),
  'topology': 'single-node',
  'nodeCount': 1,
  'measuredRpoSeconds': int(os.environ['RPO_SECONDS']),
  'measuredRtoSeconds': int(os.environ['RTO_SECONDS']),
  'evidence': {
    'singleNodeRecoveryDrill': os.environ['SINGLE_NODE_RECOVERY_EVIDENCE_ID'],
    'offsiteBackup': os.environ['OFFSITE_BACKUP_EVIDENCE_ID'],
    'pointInTimeRecovery': os.environ['PITR_EVIDENCE_ID'],
    'penetrationTest': os.environ['PEN_TEST_EVIDENCE_ID'],
    'paymentRecovery': os.environ['PAYMENT_CHAOS_EVIDENCE_ID'],
    'alerting': os.environ['ALERTING_EVIDENCE_ID'],
    'financeReconciliation': os.environ['FINANCE_RECONCILIATION_EVIDENCE_ID'],
    'privacyReview': os.environ['PRIVACY_REVIEW_EVIDENCE_ID'],
    'privilegedMfa': os.environ['MFA_EVIDENCE_ID'],
  },
  'attestedByProtectedEnvironment': True,
  'productionReadinessGranted': True,
  'enterpriseHACertificationGranted': False,
}
Path('certification-evidence/single-node-production-readiness.json').write_text(json.dumps(out, indent=2) + '\n')
print('Single-node production readiness evidence valid; enterprise HA certification was not granted.')
PYJSON
