#!/usr/bin/env bash
set -Eeuo pipefail

# Fail-closed certification evidence gate. Values must be populated in a protected
# enterprise-certification environment only after the real drills/reviews are complete.
require_id() {
  local name="$1" value="${!1:-}"
  [[ "$value" =~ ^[A-Za-z0-9._:/-]{6,256}$ ]] || { echo "::error::$name is required and must be a stable evidence identifier" >&2; exit 2; }
}
require_status() {
  local name="$1" value="${!1:-}"
  [[ "$value" == "PASS" ]] || { echo "::error::$name must be PASS" >&2; exit 2; }
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
[[ "${PRODUCTION_TOPOLOGY:-}" == "enterprise-ha" ]] || { echo '::error::Enterprise certification requires PRODUCTION_TOPOLOGY=enterprise-ha' >&2; exit 2; }
[[ "${HA_NODE_COUNT:-}" =~ ^[2-9][0-9]*$ ]] || { echo '::error::HA_NODE_COUNT must be at least 2 for enterprise certification' >&2; exit 2; }
[[ "${RPO_SECONDS:-}" =~ ^[0-9]+$ && "${RTO_SECONDS:-}" =~ ^[0-9]+$ ]] || { echo '::error::Measured RPO_SECONDS and RTO_SECONDS are required numeric values' >&2; exit 2; }
[[ "$RPO_SECONDS" -le "${MAX_RPO_SECONDS:-300}" ]] || { echo "::error::Measured RPO exceeds ${MAX_RPO_SECONDS:-300}s" >&2; exit 2; }
[[ "$RTO_SECONDS" -le "${MAX_RTO_SECONDS:-1800}" ]] || { echo "::error::Measured RTO exceeds ${MAX_RTO_SECONDS:-1800}s" >&2; exit 2; }
[[ "${CERTIFIED_SHA:-}" =~ ^[0-9a-f]{40}$ ]] || { echo '::error::CERTIFIED_SHA must be the exact 40-character release SHA' >&2; exit 2; }
mkdir -p certification-evidence
python3 - <<'PY'
import json, os
from pathlib import Path
out = {
  'schemaVersion': 1,
  'releaseSha': os.environ['CERTIFIED_SHA'],
  'status': 'PASS',
  'certifiedAtUtc': __import__('datetime').datetime.now(__import__('datetime').timezone.utc).isoformat(),
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
print('enterprise evidence manifest valid')
PY
