#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/infra/certification/verify-enterprise-evidence.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/empty/certification-evidence" "$TMP/valid" "$TMP/over-limit"
printf 'stale manifest must be removed\n' > "$TMP/empty/certification-evidence/enterprise-certification.json"

# An empty environment must report all missing settings in a single attempt,
# not stop at the first evidence ID. It must not create any certification output.
set +e
missing_output="$(cd "$TMP/empty" && env -i PATH="$PATH" bash "$SCRIPT" 2>&1)"
missing_rc=$?
set -e
[[ "$missing_rc" -eq 2 ]] || { printf 'Expected missing-evidence rc=2, got %s\n%s\n' "$missing_rc" "$missing_output" >&2; exit 1; }
for name in \
  HA_FAILOVER_EVIDENCE_ID PITR_EVIDENCE_ID PEN_TEST_EVIDENCE_ID PAYMENT_CHAOS_EVIDENCE_ID \
  ALERTING_EVIDENCE_ID FINANCE_RECONCILIATION_EVIDENCE_ID PRIVACY_REVIEW_EVIDENCE_ID MFA_EVIDENCE_ID \
  HA_FAILOVER_STATUS PITR_STATUS PEN_TEST_STATUS PAYMENT_CHAOS_STATUS ALERTING_STATUS \
  FINANCE_RECONCILIATION_STATUS PRIVACY_REVIEW_STATUS MFA_STATUS \
  PRODUCTION_TOPOLOGY HA_NODE_COUNT RPO_SECONDS RTO_SECONDS MAX_RPO_SECONDS MAX_RTO_SECONDS CERTIFIED_SHA; do
  grep -q "$name" <<< "$missing_output" || { echo "Missing aggregated diagnostic for $name" >&2; exit 1; }
done
[[ ! -e "$TMP/empty/certification-evidence/enterprise-certification.json" ]] || { echo 'A failed evidence check must not create a certification manifest' >&2; exit 1; }

COMMON_ENV=(
  PRODUCTION_TOPOLOGY=enterprise-ha HA_NODE_COUNT=10 RPO_SECONDS=60 RTO_SECONDS=300
  MAX_RPO_SECONDS=300 MAX_RTO_SECONDS=1800 CERTIFIED_SHA=0123456789abcdef0123456789abcdef01234567
  HA_FAILOVER_EVIDENCE_ID=fixture-ha-001 PITR_EVIDENCE_ID=fixture-pitr-001
  PEN_TEST_EVIDENCE_ID=fixture-pentest-001 PAYMENT_CHAOS_EVIDENCE_ID=fixture-payment-001
  ALERTING_EVIDENCE_ID=fixture-alerting-001 FINANCE_RECONCILIATION_EVIDENCE_ID=fixture-finance-001
  PRIVACY_REVIEW_EVIDENCE_ID=fixture-privacy-001 MFA_EVIDENCE_ID=fixture-mfa-001
  HA_FAILOVER_STATUS=PASS PITR_STATUS=PASS PEN_TEST_STATUS=PASS PAYMENT_CHAOS_STATUS=PASS
  ALERTING_STATUS=PASS FINANCE_RECONCILIATION_STATUS=PASS PRIVACY_REVIEW_STATUS=PASS MFA_STATUS=PASS
)

# Test fixtures live only in temp storage; they are not real operational certification evidence.
(cd "$TMP/valid" && env -i PATH="$PATH" "${COMMON_ENV[@]}" bash "$SCRIPT")
python3 - "$TMP/valid/certification-evidence/enterprise-certification.json" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
d = json.loads(p.read_text())
assert d['status'] == 'PASS'
assert d['releaseSha'] == '0123456789abcdef0123456789abcdef01234567'
assert d['topology'] == 'enterprise-ha'
assert d['haNodeCount'] == 10, 'multi-digit HA node counts must be accepted'
assert len(d['evidence']) == 8
assert d['attestedByProtectedEnvironment'] is True
PY

set +e
limit_output="$(cd "$TMP/over-limit" && env -i PATH="$PATH" "${COMMON_ENV[@]}" RPO_SECONDS=301 bash "$SCRIPT" 2>&1)"
limit_rc=$?
set -e
[[ "$limit_rc" -eq 2 ]] || { printf 'Expected over-limit RPO rc=2, got %s\n%s\n' "$limit_rc" "$limit_output" >&2; exit 1; }
grep -q 'Measured RPO (301s) exceeds MAX_RPO_SECONDS (300s)' <<< "$limit_output"
[[ ! -e "$TMP/over-limit/certification-evidence/enterprise-certification.json" ]]
echo 'PASS: enterprise evidence validation aggregates missing settings, preserves hard RPO/RTO checks, accepts valid multi-digit HA counts, and never emits a manifest for invalid evidence.'
