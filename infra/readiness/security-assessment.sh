#!/usr/bin/env bash
# Security evidence. Default mode is SELF-RUN and must never populate PEN_TEST_*.
# Self-run:
#   ASSESSMENT_TYPE=self-run RELEASE_SHA=<sha> DAST_REPORT_DIR=./dast-artifact \
#   FINDINGS_FILE=./findings.csv BASE_URL=https://staging-events.neelastack.com OPERATOR='Name' bash ...
# Independent report mode (only after a real external test and review):
#   ASSESSMENT_TYPE=independent INDEPENDENT_PEN_TEST_REPORT=./external-report.pdf \
#   INDEPENDENT_ASSESSOR_NAME='External firm' INDEPENDENT_PEN_TEST_REVIEWER='Reviewer' \
#   INDEPENDENT_PEN_TEST_RELEASE_SHA=<same SHA> INDEPENDENT_PEN_TEST_REVIEWED=yes ... <same scan inputs>
# FINDINGS_FILE must have header id,severity,status,note and at least one explicit row. If no findings,
# document that explicitly with an INFO row; an empty CSV is not accepted as evidence.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "$SCRIPT_DIR/lib.sh"
: "${RELEASE_SHA:?RELEASE_SHA is required}"
: "${BASE_URL:?BASE_URL is required}"
: "${DAST_REPORT_DIR:?DAST_REPORT_DIR is required}"
FINDINGS_FILE="${FINDINGS_FILE:-}"
ASSESSMENT_TYPE="${ASSESSMENT_TYPE:-self-run}"
need_operator
VARPREFIX=SECURITY_SELF_ASSESSMENT
BUNDLE=()
if [[ "$ASSESSMENT_TYPE" == independent ]]; then
  : "${INDEPENDENT_PEN_TEST_REPORT:?Set this only to a real external assessor's report}"
  : "${INDEPENDENT_ASSESSOR_NAME:?Name the independent assessor/firm}"
  : "${INDEPENDENT_PEN_TEST_REVIEWER:?Name the responsible reviewer of the report}"
  : "${INDEPENDENT_PEN_TEST_RELEASE_SHA:?Set the release SHA actually assessed}"
  [[ -f "$INDEPENDENT_PEN_TEST_REPORT" && -s "$INDEPENDENT_PEN_TEST_REPORT" ]] || { echo "Independent report is missing or empty." >&2; exit 2; }
  [[ "$INDEPENDENT_PEN_TEST_RELEASE_SHA" == "$RELEASE_SHA" ]] || { echo "Independent assessment release SHA must match RELEASE_SHA exactly." >&2; exit 2; }
  [[ "${INDEPENDENT_PEN_TEST_REVIEWED:-no}" == yes ]] || { echo "Set INDEPENDENT_PEN_TEST_REVIEWED=yes only after a responsible reviewer has reviewed the independent report." >&2; exit 2; }
  [[ "${INDEPENDENT_ASSESSOR_NAME,,}" != "${OPERATOR,,}" ]] || { echo "Independent assessor must differ from the person running the script." >&2; exit 2; }
  [[ "${INDEPENDENT_PEN_TEST_REVIEWER,,}" != "${OPERATOR,,}" && "${INDEPENDENT_PEN_TEST_REVIEWER,,}" != "${INDEPENDENT_ASSESSOR_NAME,,}" ]] || { echo "Independent report reviewer must differ from both operator and assessor." >&2; exit 2; }
  VARPREFIX=PEN_TEST
elif [[ "$ASSESSMENT_TYPE" != self-run ]]; then
  echo "ASSESSMENT_TYPE must be self-run or independent." >&2; exit 2
fi
# Default to staging. Scanning production requires an explicit, documented scope override.
HOST="$(python3 - "$BASE_URL" <<'PY'
import sys, urllib.parse
try: print((urllib.parse.urlsplit(sys.argv[1]).hostname or '').lower())
except Exception: print('')
PY
)"
if [[ "$HOST" != staging-events.neelastack.com && "${CONFIRM_NONSTAGING_SECURITY_TARGET:-no}" != yes ]]; then
  echo "Refusing to scan '$HOST'. Use https://staging-events.neelastack.com or explicitly set CONFIRM_NONSTAGING_SECURITY_TARGET=yes for an approved scope." >&2; exit 2
fi
begin_report security-assessment "$VARPREFIX" "Security assessment ($ASSESSMENT_TYPE; self-run scan is not independent by itself)"
say "Assessment type: $ASSESSMENT_TYPE"
say "Target: $BASE_URL"
if [[ "$ASSESSMENT_TYPE" == independent ]]; then
  EXT_BASENAME="independent-pen-test-report-$(basename "$INDEPENDENT_PEN_TEST_REPORT")"
  cp -- "$INDEPENDENT_PEN_TEST_REPORT" "$EVIDENCE_DIR/$EXT_BASENAME"
  chmod 600 "$EVIDENCE_DIR/$EXT_BASENAME"
  BUNDLE+=("$EXT_BASENAME")
  pass "independent assessor report attached: $INDEPENDENT_ASSESSOR_NAME; report reviewer: $INDEPENDENT_PEN_TEST_REVIEWER; exact release SHA matched"
else
  info "This is a self-run assessment only; it does not satisfy independent penetration-test evidence."
fi

# Require a valid ZAP JSON document (not just any file or malformed JSON) and count critical/high alerts.
if [[ -d "$DAST_REPORT_DIR" ]]; then
  mkdir -p "$EVIDENCE_DIR/dast-$RELEASE_SHA"
  cp -r "$DAST_REPORT_DIR"/. "$EVIDENCE_DIR/dast-$RELEASE_SHA/"
  BUNDLE+=("dast-$RELEASE_SHA")
  ZAP_RESULT="$(python3 - "$DAST_REPORT_DIR" "$HOST" <<'PY'
import json, pathlib, sys
root=pathlib.Path(sys.argv[1]); target_host=sys.argv[2].lower(); valid=0; critical=0; high=0
for f in root.rglob('*.json'):
    try: d=json.loads(f.read_text(encoding='utf-8'))
    except Exception: continue
    if not isinstance(d,dict) or not isinstance(d.get('site'),list) or not d['site']: continue
    shaped=True; matching_sites=[]
    for site in d['site']:
        if not isinstance(site,dict) or not isinstance(site.get('alerts'),list): shaped=False; break
        site_name=str(site.get('@name') or site.get('name') or '').lower()
        if not site_name: shaped=False; break
        if target_host in site_name: matching_sites.append(site)
    if not shaped or not matching_sites: continue
    valid += 1
    for site in matching_sites:
        for alert in site.get('alerts',[]):
            try: risk=str(alert.get('riskcode','0'))
            except Exception: risk='0'
            if risk=='4': critical += 1
            elif risk=='3': high += 1
print(f'{valid}|{critical}|{high}')
PY
)"
  IFS='|' read -r VALID_ZAP CRITICAL HIGH <<< "$ZAP_RESULT"
  if [[ "$VALID_ZAP" =~ ^[0-9]+$ ]] && (( VALID_ZAP > 0 )); then
    pass "valid ZAP JSON report(s) parsed: $VALID_ZAP"
    say "ZAP critical alerts: $CRITICAL; high alerts: $HIGH"
    if (( CRITICAL == 0 && HIGH == 0 )); then pass "ZAP reports zero critical/high alerts"; else fail "ZAP reports $CRITICAL critical and $HIGH high alert(s); review findings and remediate/block before release"; fi
  else
    fail "No valid ZAP JSON report found; an arbitrary nonempty artifact directory is insufficient"
  fi
else
  fail "DAST artifact directory missing: download the report artifact from the matching qualification run"
fi

H="$(mktemp)"; curl -sIL --max-time 25 "$BASE_URL/" -o "$H" || true
for h in strict-transport-security content-security-policy x-content-type-options referrer-policy; do
  grep -qi "^$h:" "$H" && pass "header present: $h" || fail "header missing on $BASE_URL/: $h"
done
rm -f "$H"
code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$BASE_URL/api/v1/admin/dashboard" || echo 000)"
[[ "$code" == 401 || "$code" == 403 ]] && pass "admin API rejects anonymous access ($code)" || fail "admin API returned $code to anonymous request"
code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "http://${BASE_URL#*://}/" || echo 000)"
[[ "$code" =~ ^(301|302|307|308|000)$ ]] && pass "plain HTTP is redirected or refused ($code)" || fail "plain HTTP answered $code"

if [[ -n "$FINDINGS_FILE" && -f "$FINDINGS_FILE" ]]; then
  say ""; say "== Findings =="
  cp "$FINDINGS_FILE" "$EVIDENCE_DIR/findings-$RELEASE_SHA.csv"; BUNDLE+=("findings-$RELEASE_SHA.csv")
  set +e
  python3 - "$FINDINGS_FILE" >"$EVIDENCE_DIR/.findings-check.tmp" <<'PY'
import csv, sys
bad=0
with open(sys.argv[1], newline='', encoding='utf-8-sig') as fh:
    reader=csv.DictReader(fh)
    required={'id','severity','status','note'}
    if not reader.fieldnames or not required.issubset({h.strip().lower() for h in reader.fieldnames}):
        print('FAIL  findings CSV must have id,severity,status,note headers'); sys.exit(1)
    rows=list(reader)
if not rows:
    print('FAIL  findings CSV is empty; explicitly document no findings with an INFO row'); sys.exit(1)
for r in rows:
    # Normalize header capitalization while preserving values.
    r={str(k).strip().lower(): (v or '').strip() for k,v in r.items() if k is not None}
    ident=r.get('id',''); sev=r.get('severity','').upper(); st=r.get('status','').upper(); note=r.get('note','')
    hi=sev in ('CRITICAL','HIGH')
    if not ident or st not in ('FIXED','ACCEPTED','OPEN') or sev not in ('CRITICAL','HIGH','MEDIUM','LOW','INFO'):
        print(f'FAIL  finding {ident or "<missing id>"}: invalid ID/severity/status'); bad+=1
    elif hi and st=='OPEN': print(f'FAIL  finding {ident} ({sev}) is still OPEN'); bad+=1
    elif hi and st=='ACCEPTED' and len(note)<40: print(f'FAIL  finding {ident} ({sev}) accepted without a 40-character justification'); bad+=1
    else: print(f'PASS  finding {ident}: {sev} {st}')
sys.exit(1 if bad else 0)
PY
  rc=$?; set -e
  tee -a "$REPORT" < "$EVIDENCE_DIR/.findings-check.tmp"; rm -f "$EVIDENCE_DIR/.findings-check.tmp"
  (( rc == 0 )) || FAILS=$((FAILS+1))
else
  fail "FINDINGS_FILE is required (CSV: id,severity,status,note); empty findings must be recorded explicitly"
fi
say ""; say "== Scope checklist (answer only for actions actually performed) =="
checklist "$SCRIPT_DIR/checklists/security.txt"
finish_report "${BUNDLE[@]}"
