#!/usr/bin/env bash
# Database-free regression tests for the readiness kit helpers (checklist rules, variable output, publisher guard).
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
printf 'a1=yes\na2=yes\n' > "$T/ok.txt"
ANSWERS_FILE="$T/ok.txt" run
grep -q '^DEMO_STATUS=PASS
DEMO_REPORT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.txt' -print -quit)"
[[ -n "$DEMO_REPORT" ]] || { echo "FAIL: demo report missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_REPORT").sha256" >/dev/null ) || { echo "FAIL: report sidecar does not match final report bytes"; exit 1; }
DEMO_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
[[ -n "$DEMO_RESULT" ]] || { echo "FAIL: machine-readable result missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_RESULT").sha256" >/dev/null ) || { echo "FAIL: result JSON sidecar mismatch"; exit 1; }
python3 - "$DEMO_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["schemaVersion"] == 1 and d["kind"] == "readiness-evidence-result"
assert d["controlId"] == "DEMO" and d["status"] == "PASS"
assert d["checks"]["failCount"] == 0 and d["productionCertificationGranted"] is False
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\n' > "$T/missing.txt"
if ANSWERS_FILE="$T/missing.txt" run; then echo "FAIL: unanswered question passed"; exit 1; fi
grep -q '^DEMO_STATUS=FAIL
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
bash infra/readiness/publish-variables.sh "$T/partial.env" | grep -q 'Readiness is BLOCKED'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed blocked readiness publication"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
 "$EVIDENCE_DIR/variables.env" || { echo "FAIL: failed rerun did not replace local PASS status"; exit 1; }
DEMO_FAILED_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
python3 - "$DEMO_FAILED_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["status"] == "FAIL"
assert d["checks"]["failCount"] > 0
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
if bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted partial values"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
 "$EVIDENCE_DIR/variables.env" && grep -Eq '^DEMO_EVIDENCE_ID=production/readiness-evidence/demo-[0-9TZ]+\.txt
DEMO_REPORT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.txt' -print -quit)"
[[ -n "$DEMO_REPORT" ]] || { echo "FAIL: demo report missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_REPORT").sha256" >/dev/null ) || { echo "FAIL: report sidecar does not match final report bytes"; exit 1; }
DEMO_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
[[ -n "$DEMO_RESULT" ]] || { echo "FAIL: machine-readable result missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_RESULT").sha256" >/dev/null ) || { echo "FAIL: result JSON sidecar mismatch"; exit 1; }
python3 - "$DEMO_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["schemaVersion"] == 1 and d["kind"] == "readiness-evidence-result"
assert d["controlId"] == "DEMO" and d["status"] == "PASS"
assert d["checks"]["failCount"] == 0 and d["productionCertificationGranted"] is False
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\n' > "$T/missing.txt"
if ANSWERS_FILE="$T/missing.txt" run; then echo "FAIL: unanswered question passed"; exit 1; fi
grep -q '^DEMO_STATUS=FAIL
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
bash infra/readiness/publish-variables.sh "$T/partial.env" | grep -q 'Readiness is BLOCKED'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed blocked readiness publication"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
 "$EVIDENCE_DIR/variables.env" || { echo "FAIL: failed rerun did not replace local PASS status"; exit 1; }
DEMO_FAILED_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
python3 - "$DEMO_FAILED_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["status"] == "FAIL"
assert d["checks"]["failCount"] > 0
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
if bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted partial values"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
 "$EVIDENCE_DIR/variables.env" && grep -Eq '^DEMO_RESULT_ID=production/readiness-evidence/demo-[0-9TZ]+\.result\.json
DEMO_REPORT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.txt' -print -quit)"
[[ -n "$DEMO_REPORT" ]] || { echo "FAIL: demo report missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_REPORT").sha256" >/dev/null ) || { echo "FAIL: report sidecar does not match final report bytes"; exit 1; }
DEMO_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
[[ -n "$DEMO_RESULT" ]] || { echo "FAIL: machine-readable result missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_RESULT").sha256" >/dev/null ) || { echo "FAIL: result JSON sidecar mismatch"; exit 1; }
python3 - "$DEMO_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["schemaVersion"] == 1 and d["kind"] == "readiness-evidence-result"
assert d["controlId"] == "DEMO" and d["status"] == "PASS"
assert d["checks"]["failCount"] == 0 and d["productionCertificationGranted"] is False
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\n' > "$T/missing.txt"
if ANSWERS_FILE="$T/missing.txt" run; then echo "FAIL: unanswered question passed"; exit 1; fi
grep -q '^DEMO_STATUS=FAIL
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
bash infra/readiness/publish-variables.sh "$T/partial.env" | grep -q 'Readiness is BLOCKED'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed blocked readiness publication"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
 "$EVIDENCE_DIR/variables.env" || { echo "FAIL: failed rerun did not replace local PASS status"; exit 1; }
DEMO_FAILED_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
python3 - "$DEMO_FAILED_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["status"] == "FAIL"
assert d["checks"]["failCount"] > 0
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
if bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted partial values"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
 "$EVIDENCE_DIR/variables.env"
DEMO_REPORT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.txt' -print -quit)"
[[ -n "$DEMO_REPORT" ]] || { echo "FAIL: demo report missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_REPORT").sha256" >/dev/null ) || { echo "FAIL: report sidecar does not match final report bytes"; exit 1; }
DEMO_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
[[ -n "$DEMO_RESULT" ]] || { echo "FAIL: machine-readable result missing"; exit 1; }
( cd "$EVIDENCE_DIR" && sha256sum -c "$(basename "$DEMO_RESULT").sha256" >/dev/null ) || { echo "FAIL: result JSON sidecar mismatch"; exit 1; }
python3 - "$DEMO_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["schemaVersion"] == 1 and d["kind"] == "readiness-evidence-result"
assert d["controlId"] == "DEMO" and d["status"] == "PASS"
assert d["checks"]["failCount"] == 0 and d["productionCertificationGranted"] is False
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\n' > "$T/missing.txt"
if ANSWERS_FILE="$T/missing.txt" run; then echo "FAIL: unanswered question passed"; exit 1; fi
grep -q '^DEMO_STATUS=FAIL
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
bash infra/readiness/publish-variables.sh "$T/partial.env" | grep -q 'Readiness is BLOCKED'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed blocked readiness publication"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
 "$EVIDENCE_DIR/variables.env" || { echo "FAIL: failed rerun did not replace local PASS status"; exit 1; }
DEMO_FAILED_RESULT="$(find "$EVIDENCE_DIR" -maxdepth 1 -type f -name 'demo-*.result.json' -print -quit)"
python3 - "$DEMO_FAILED_RESULT" <<'PY'
import json, sys
from pathlib import Path
d=json.loads(Path(sys.argv[1]).read_text())
assert d["status"] == "FAIL"
assert d["checks"]["failCount"] > 0
PY
rm -rf "$EVIDENCE_DIR"
printf 'a1=yes\na2=na:short\n' > "$T/na.txt"
if ANSWERS_FILE="$T/na.txt" run; then echo "FAIL: short N/A reason passed"; exit 1; fi
printf 'a1=yes\na2=na:not applicable because no refunds exist yet in this deployment\n' > "$T/na2.txt"
ANSWERS_FILE="$T/na2.txt" run
# Publisher: refuses partial, accepts complete.
printf 'MFA_STATUS=PASS\n' > "$T/partial.env"
if bash infra/readiness/publish-variables.sh "$T/partial.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted partial values"; exit 1; fi
: > "$T/full.env"
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do printf '%s_STATUS=PASS\n%s_EVIDENCE_ID=production/readiness-evidence/test/%s-report.txt\n' "$p" "$p" "$p" >> "$T/full.env"; done
printf 'RPO_SECONDS=60\nRTO_SECONDS=900\n' >> "$T/full.env"
bash infra/readiness/publish-variables.sh "$T/full.env" | grep -q 'Values are NOT published'
if APPLY=yes bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher allowed GitHub write without R2 verification"; exit 1; fi
cp "$T/full.env" "$T/duplicate.env"; echo 'RTO_SECONDS=900' >> "$T/duplicate.env"
if bash infra/readiness/publish-variables.sh "$T/duplicate.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted duplicate variable keys"; exit 1; fi
sed -i 's/^RPO_SECONDS=60/RPO_SECONDS=9999/' "$T/full.env"
if bash infra/readiness/publish-variables.sh "$T/full.env" >/dev/null 2>&1; then echo "FAIL: publisher accepted RPO above maximum"; exit 1; fi
# Self-run security checks must not write the required independent PEN_TEST variable.
grep -q '^VARPREFIX=SECURITY_SELF_ASSESSMENT$' infra/readiness/security-assessment.sh
if ASSESSMENT_TYPE=independent OPERATOR=Tester RELEASE_SHA=fff2632c45dcc9b5b14b5e40190f60d876f8e147 BASE_URL=https://staging-events.neelastack.com DAST_REPORT_DIR="$T/missing-dast" bash infra/readiness/security-assessment.sh >/dev/null 2>&1; then
  echo "FAIL: security script accepted independent mode without an external report"; exit 1
fi
grep -q 'exact same valid TOTP challenge/code was rejected as replay' infra/readiness/checklists/mfa.txt
grep -q 'second enabled admin with MFA reset a disposable staging user' infra/readiness/checklists/mfa.txt
echo "PASS: readiness kit checksum, checklist, security separation, and publication guards behave correctly."
