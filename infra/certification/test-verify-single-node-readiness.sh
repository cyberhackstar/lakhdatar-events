#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/infra/certification/verify-single-node-readiness.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/missing/certification-evidence" "$TMP/valid" "$TMP/wrong-topology" "$TMP/over-limit"
printf 'stale pass\n' > "$TMP/missing/certification-evidence/single-node-production-readiness.json"
set +e
missing_output="$(cd "$TMP/missing" && env -i PATH="$PATH" bash "$SCRIPT" 2>&1)"
missing_rc=$?
set -e
[[ "$missing_rc" -eq 2 ]] || { printf 'Expected missing-evidence rc=2, got %s\n%s\n' "$missing_rc" "$missing_output" >&2; exit 1; }
for name in SINGLE_NODE_RECOVERY_EVIDENCE_ID OFFSITE_BACKUP_EVIDENCE_ID PITR_EVIDENCE_ID PEN_TEST_EVIDENCE_ID PAYMENT_CHAOS_EVIDENCE_ID ALERTING_EVIDENCE_ID FINANCE_RECONCILIATION_EVIDENCE_ID PRIVACY_REVIEW_EVIDENCE_ID MFA_EVIDENCE_ID SINGLE_NODE_RECOVERY_STATUS OFFSITE_BACKUP_STATUS PITR_STATUS PEN_TEST_STATUS PAYMENT_CHAOS_STATUS ALERTING_STATUS FINANCE_RECONCILIATION_STATUS PRIVACY_REVIEW_STATUS MFA_STATUS PRODUCTION_TOPOLOGY HA_NODE_COUNT RPO_SECONDS RTO_SECONDS MAX_RPO_SECONDS MAX_RTO_SECONDS CERTIFIED_SHA; do
  grep -q "$name" <<< "$missing_output" || { echo "Missing aggregated diagnostic for $name" >&2; exit 1; }
done
[[ ! -e "$TMP/missing/certification-evidence/single-node-production-readiness.json" ]] || { echo 'Stale manifest survived invalid rerun' >&2; exit 1; }
COMMON_ENV=(
 PRODUCTION_TOPOLOGY=single-node HA_NODE_COUNT=1 RPO_SECONDS=60 RTO_SECONDS=300 MAX_RPO_SECONDS=300 MAX_RTO_SECONDS=1800 CERTIFIED_SHA=0123456789abcdef0123456789abcdef01234567
 SINGLE_NODE_RECOVERY_EVIDENCE_ID=fixture-recovery-001 OFFSITE_BACKUP_EVIDENCE_ID=fixture-backup-001 PITR_EVIDENCE_ID=fixture-pitr-001 PEN_TEST_EVIDENCE_ID=fixture-pentest-001 PAYMENT_CHAOS_EVIDENCE_ID=fixture-payment-001 ALERTING_EVIDENCE_ID=fixture-alerting-001 FINANCE_RECONCILIATION_EVIDENCE_ID=fixture-finance-001 PRIVACY_REVIEW_EVIDENCE_ID=fixture-privacy-001 MFA_EVIDENCE_ID=fixture-mfa-001
 SINGLE_NODE_RECOVERY_STATUS=PASS OFFSITE_BACKUP_STATUS=PASS PITR_STATUS=PASS PEN_TEST_STATUS=PASS PAYMENT_CHAOS_STATUS=PASS ALERTING_STATUS=PASS FINANCE_RECONCILIATION_STATUS=PASS PRIVACY_REVIEW_STATUS=PASS MFA_STATUS=PASS
)
(cd "$TMP/valid" && env -i PATH="$PATH" "${COMMON_ENV[@]}" bash "$SCRIPT")
python3 - "$TMP/valid/certification-evidence/single-node-production-readiness.json" <<'PYVALIDATE'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d['status']=='PASS'
assert d['releaseSha']=='0123456789abcdef0123456789abcdef01234567'
assert d['topology']=='single-node' and d['nodeCount']==1
assert d['productionReadinessGranted'] is True
assert d['enterpriseHACertificationGranted'] is False
assert d['readinessType']=='single-node-production-readiness'
assert len(d['evidence'])==9
PYVALIDATE
set +e
topology_output="$(cd "$TMP/wrong-topology" && env -i PATH="$PATH" "${COMMON_ENV[@]}" PRODUCTION_TOPOLOGY=enterprise-ha bash "$SCRIPT" 2>&1)"
topology_rc=$?
set -e
[[ "$topology_rc" -eq 2 ]] && grep -q 'PRODUCTION_TOPOLOGY must be single-node' <<< "$topology_output"
set +e
rpo_output="$(cd "$TMP/over-limit" && env -i PATH="$PATH" "${COMMON_ENV[@]}" RPO_SECONDS=301 bash "$SCRIPT" 2>&1)"
rpo_rc=$?
set -e
[[ "$rpo_rc" -eq 2 ]] && grep -q 'Measured RPO (301s) exceeds MAX_RPO_SECONDS (300s)' <<< "$rpo_output"
[[ ! -e "$TMP/over-limit/certification-evidence/single-node-production-readiness.json" ]]
echo 'PASS: single-node readiness aggregates missing evidence, deletes stale output, validates topology/RPO, and never grants HA certification.'
