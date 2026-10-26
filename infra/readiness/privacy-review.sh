#!/usr/bin/env bash
# Privacy review evidence: public legal pages, secret/PII leakage in logs, and an owner checklist.
#   BASE_URL=https://staging-events.neelastack.com RELEASE_SHA=<exact-qualified-release-sha> \
#   PRIVACY_REVIEWER='Business/Legal Owner' LOG_CONTAINERS="lakhdatar-staging-backend lakhdatar-staging-web" OPERATOR='Name' \
#   bash infra/readiness/privacy-review.sh        (LOG_FILES="/path/*.log" also supported)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "$SCRIPT_DIR/lib.sh"
: "${BASE_URL:?BASE_URL is required}"
: "${RELEASE_SHA:?RELEASE_SHA (the exact app release reviewed on staging) is required}"
HOST="$(python3 - "$BASE_URL" <<'PYHOST'
import sys, urllib.parse
print((urllib.parse.urlsplit(sys.argv[1]).hostname or '').lower())
PYHOST
)"
if [[ "$HOST" != staging-events.neelastack.com && "${CONFIRM_NONSTAGING_PRIVACY_TARGET:-no}" != yes ]]; then
  echo "Refusing privacy scan against '$HOST'. Review staging before launch, or explicitly confirm an approved non-staging review target." >&2; exit 2
fi
begin_report privacy-review PRIVACY_REVIEW "Privacy and data governance review"
PRIVACY_REVIEWER="${PRIVACY_REVIEWER:-}"
if [[ -z "$PRIVACY_REVIEWER" ]]; then
  [[ -t 0 ]] || { fail "PRIVACY_REVIEWER (responsible business/legal owner) is required"; PRIVACY_REVIEWER="UNSET"; }
  if [[ -t 0 ]]; then read -r -p "Responsible business/legal reviewer's name (must differ from operator): " PRIVACY_REVIEWER; fi
fi
if [[ -n "$PRIVACY_REVIEWER" && "$PRIVACY_REVIEWER" != "UNSET" && "${PRIVACY_REVIEWER,,}" != "${OPERATOR,,}" ]]; then
  pass "Responsible privacy/legal reviewer recorded: $PRIVACY_REVIEWER"
else
  fail "PRIVACY_REVIEWER must be set to a named business/legal owner other than OPERATOR"
fi
for page in privacy terms refund-policy; do
  body="$(mktemp)"; read -r code url < <(curl -sL --max-time 25 -o "$body" -w '%{http_code} %{url_effective}\n' "$BASE_URL/$page" || echo "000 -")
  if [[ "$code" == 200 && "$url" != *not-found* ]] && grep -qi "$(echo "$page" | tr - ' ' | sed 's/refund policy/refund/')" "$body" && [[ "$(wc -c < "$body")" -gt 2000 ]]; then pass "/$page is published"
  else fail "/$page is not published at $BASE_URL/$page (HTTP $code). A public site collecting names/emails/phones needs a Privacy Policy, Terms and refund policy page"; fi
  rm -f "$body"
done
PATTERNS='eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.|(password|passwd|secret|api[_-]?key|authorization)["'"'"' :=]+[^ ,"'"'"'}]{6,}|x-webhook-signature|razorpay_signature|BEGIN (RSA |EC )?PRIVATE KEY'
LOGS=""
for c in ${LOG_CONTAINERS:-}; do LOGS+="$(docker logs --since "${LOG_SINCE:-72h}" "$c" 2>&1 | head -200000)"$'\n'; done
for f in ${LOG_FILES:-}; do [[ -f "$f" ]] && LOGS+="$(tail -n 200000 "$f")"$'\n'; done
if [[ -z "${LOGS//[$'\n ']/}" ]]; then fail "no logs were scanned (set LOG_CONTAINERS or LOG_FILES)"
else
  HITS="$(grep -Eic "$PATTERNS" <<< "$LOGS" || true)"; say "Log lines scanned: $(wc -l <<< "$LOGS")"
  [[ "$HITS" == 0 ]] && pass "no tokens, passwords, secrets or signatures found in logs" || fail "$HITS log line(s) match secret patterns; inspect and fix logging (matches are not copied into this report)"
  EM="$(grep -Eoc '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[a-z]{2,}' <<< "$LOGS" || true)"; say "Log lines containing email addresses: $EM"
  (( EM <= ${MAX_EMAIL_LOG_LINES:-0} )) && pass "customer emails are not written to logs" || fail "$EM log line(s) contain email addresses (PII in logs)"
fi
say ""; say "== Owner checklist =="
checklist "$SCRIPT_DIR/checklists/privacy.txt"
finish_report
