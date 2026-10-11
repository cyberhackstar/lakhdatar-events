#!/usr/bin/env bash
set -Eeuo pipefail

# Fail-closed certification evidence gate. Evidence IDs and PASS states must be
# entered only after the corresponding real exercises/reviews have completed.
# Collect every configuration error in one run so operators do not discover
# missing environment values one-by-one over repeated workflow executions.
issues=()
add_issue() { issues+=("$1"); }
require_id() {
  local name="$1" value="${!1:-}"
  [[ "$value" =~ ^[A-Za-z0-9._:/-]{6,256}$ ]] || add_issue "$name is missing or invalid (expected a stable 6-256 character evidence identifier)"
}
require_status() {
  local name="$1" value="${!1:-}"
  [[ "$value" == "PASS" ]] || add_issue "$name must be PASS after the real exercise/review is complete"
}
require_uint() {
  local name="$1" value="${!1:-}"
  [[ "$value" =~ ^[0-9]{1,9}$ ]] || add_issue "$name is missing or invalid (expected a non-negative integer of at most 9 digits)"
}

require_id HA_FAILOVER_EVIDENCE_ID
require_id PITR_EVIDENCE_ID
require_id PEN_TEST_EVIDENCE_ID
require_id PAYMENT_CHAOS_EVIDENCE_ID
require_id ALERTING_EVIDENCE_ID
require_id FINANCE_RECONCILIATION_EVIDENCE_ID
require_id PRIVACY_REVIEW_EVIDENCE_ID
require_id MFA_EVIDENCE_ID
require_status HA_FAILOVER_STATUS
require_status PITR_STATUS
require_status PEN_TEST_STATUS
require_status PAYMENT_CHAOS_STATUS
require_status ALERTING_STATUS
require_status FINANCE_RECONCILIATION_STATUS
require_status PRIVACY_REVIEW_STATUS
require_status MFA_STATUS

[[ "${PRODUCTION_TOPOLOGY:-}" == "enterprise-ha" ]] || add_issue 'PRODUCTION_TOPOLOGY must be enterprise-ha'
require_uint HA_NODE_COUNT
require_uint RPO_SECONDS
require_uint RTO_SECONDS
require_uint MAX_RPO_SECONDS
require_uint MAX_RTO_SECONDS
[[ "${CERTIFIED_SHA:-}" =~ ^[0-9a-f]{40}$ ]] || add_issue 'CERTIFIED_SHA must be the exact 40-character lowercase release SHA'

if [[ "${HA_NODE_COUNT:-}" =~ ^[0-9]{1,9}$ ]] && (( 10#$HA_NODE_COUNT < 2 )); then
  add_issue 'HA_NODE_COUNT must be at least 2 for enterprise certification'
fi
if [[ "${RPO_SECONDS:-}" =~ ^[0-9]{1,9}$ && "${MAX_RPO_SECONDS:-}" =~ ^[0-9]{1,9}$ ]] && (( 10#$RPO_SECONDS > 10#$MAX_RPO_SECONDS )); then
  add_issue "Measured RPO (${RPO_SECONDS}s) exceeds MAX_RPO_SECONDS (${MAX_RPO_SECONDS}s)"
fi
if [[ "${RTO_SECONDS:-}" =~ ^[0-9]{1,9}$ && "${MAX_RTO_SECONDS:-}" =~ ^[0-9]{1,9}$ ]] && (( 10#$RTO_SECONDS > 10#$MAX_RTO_SECONDS )); then
  add_issue "Measured RTO (${RTO_SECONDS}s) exceeds MAX_RTO_SECONDS (${MAX_RTO_SECONDS}s)"
fi

# Never allow a stale artifact to survive a failed re-run in a reused workspace.
if [[ -d certification-evidence ]]; then
  rm -f certification-evidence/enterprise-certification.json
fi
if ((${#issues[@]})); then
  echo '::error title=Enterprise certification evidence incomplete::Configure the following missing/invalid values in the protected enterprise-certification GitHub Environment. Do not invent evidence or mark incomplete drills PASS.' >&2
  printf '::error::%s\n' "${issues[@]}" >&2
  echo 'Documentation: docs/ENTERPRISE-CERTIFICATION-ENVIRONMENT.md' >&2
  exit 2
fi

mkdir -p certification-evidence
python3 - <<'PY'
import json, os
from datetime import datetime, timezone
from pathlib import Path
out = {
  'schemaVersion': 1,
  'releaseSha': os.environ['CERTIFIED_SHA'],
  'status': 'PASS',
  'certifiedAtUtc': datetime.now(timezone.utc).isoformat(),
  'topology': os.environ['PRODUCTION_TOPOLOGY'],
  'haNodeCount': int(os.environ['HA_NODE_COUNT']),
  'measuredRpoSeconds': int(os.environ['RPO_SECONDS']),
  'measuredRtoSeconds': int(os.environ['RTO_SECONDS']),
  'evidence': {
    'haFailover': os.environ['HA_FAILOVER_EVIDENCE_ID'],
    'pitr': os.environ['PITR_EVIDENCE_ID'],
    'penetrationTest': os.environ['PEN_TEST_EVIDENCE_ID'],
    'paymentChaos': os.environ['PAYMENT_CHAOS_EVIDENCE_ID'],
    'alerting': os.environ['ALERTING_EVIDENCE_ID'],
    'financeReconciliation': os.environ['FINANCE_RECONCILIATION_EVIDENCE_ID'],
    'privacyReview': os.environ['PRIVACY_REVIEW_EVIDENCE_ID'],
    'privilegedMfa': os.environ['MFA_EVIDENCE_ID'],
  },
  'attestedByProtectedEnvironment': True,
}
Path('certification-evidence/enterprise-certification.json').write_text(json.dumps(out, indent=2) + '\n')
print('Enterprise evidence manifest valid.')
PY
