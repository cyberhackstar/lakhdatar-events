#!/usr/bin/env bash
# Database-free regression tests for readiness report/result generation and the publication guard.
set -euo pipefail
cd "$(dirname "$0")/../.."
for f in infra/readiness/*.sh; do bash -n "$f"; done
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
export EVIDENCE_DIR="$T/ev" OPERATOR="Tester"
cat > "$T/c.txt" <<'LST'
a1|first question
a2|second question
LST
run() { ( source infra/readiness/lib.sh; begin_report demo DEMO "Demo" >/dev/null; checklist "$T/c.txt" >/dev/null; finish_report >/dev/null 2>&1 ); }

# Passing report and machine-readable result must both have valid checksums.
printf 'a1=yes\na2=yes\n' > "$T/ok.txt"
ANSWERS_FILE="$T/ok.txt" run
grep -q '^DEMO_STATUS=PASS$' "$EVIDENCE_DIR/variables.env"
grep -Eq '^DEMO_EVIDENCE_ID=production/readiness-evidence/demo-[0-9TZ]+\.txt$' "$EVIDENCE_DIR/variables.env"
grep -Eq '^DEMO_RESULT_ID=production/readiness-evidence/demo-[0-9TZ]+\.result\.json$' "$EVIDENCE_DIR/variables.env"
DEMO_REPORT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.txt' -print -quit)"
[[ -n "$DEMO_REPORT" ]] || { echo "FAIL: demo report missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_REPORT").sha256" >/dev/null )
DEMO_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
[[ -n "$DEMO_RESULT" ]] || { echo "FAIL: machine-readable result missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_RESULT").sha256" >/dev/null )
python3 - "$DEMO_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["schemaVersion"] == 1 and d["kind"] == "readiness-evidence-result"
assert d["controlId"] == "DEMO" and d["status"] == "PASS"
assert d["checks"]["failCount"] == 0 and d["productionCertificationGranted"] is False
PY

# A failed re-run must become the latest local status; it must not leave a stale PASS.
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\n' > "$T/missing.txt"
if ANSWERS_FILE="$T/missing.txt" run; then echo "FAIL: unanswered question passed"; exit 1; fi
grep -q '^DEMO_STATUS=FAIL$' "$EVIDENCE_DIR/variables.env"
DEMO_FAILED_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
python3 - "$DEMO_FAILED_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["status"] == "FAIL"
assert d["checks"]["failCount"] > 0
PY

# Checklist N/A without a meaningful reason fails; a documented reason can pass.
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run

# A reviewer cannot override a source report that explicitly failed.
rm -rf "$EVIDENCE_DIR"
printf 'Result: FAIL (1 failing check(s))\nFAIL  source invariant did not pass\n' > "$T/failed-source.txt"
if CONTROL_ID=ALERTING EVIDENCE_FILE="$T/failed-source.txt" RELEASE_SHA=0123456789abcdef0123456789abcdef01234567 \
   RESULT_SCOPE='Read only review of the prior alerting simulation and its stated limitations.' \
   OPERATOR='Operator One' INDEPENDENT_REVIEWER='Reviewer Two' REVIEW_STATUS=PASS \
   bash infra/readiness/record-reviewed-evidence.sh >/dev/null 2>&1; then
  echo "FAIL: reviewed-evidence wrapper accepted an underlying failed report"; exit 1
fi
grep -q '^ALERTING_STATUS=FAIL$' "$EVIDENCE_DIR/variables.env"
ALERTING_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'alerting-reviewed-*.result.json' -print -quit)"
python3 - "$ALERTING_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["status"] == "FAIL"
assert d["checks"]["failCount"] > 0
PY

# Publisher can verify partial evidence while readiness is blocked, but cannot publish certification variables.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
bash infra/readiness/publish-variables.sh "$T/partial.env" | grep -q 'Readiness is BLOCKED'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then
  echo "FAIL: publisher allowed blocked readiness publication"; exit 1
fi

# Complete-looking legacy file can be validated in dry-run mode but cannot write without R2 read-back.
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do
  printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"
done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then
  echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1
fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then
  echo "FAIL: publisher accepted duplicate variable keys"; exit 1
fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then
  echo "FAIL: publisher allowed RPO above maximum"; exit 1
fi

# A self-run scan remains explicitly distinct from an independent penetration test.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness reports/results, failed-rerun freshness, reviewed-source safeguards, checklist rules, and publication guards behave correctly."
