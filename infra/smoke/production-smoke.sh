#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:4002}"
PUBLIC_HOST="${PUBLIC_HOST:-events.neelastack.com}"
SMOKE_EMAIL="${SMOKE_EMAIL:-}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-}"
SMOKE_ROLE_ENDPOINT="${SMOKE_ROLE_ENDPOINT:-/api/v1/staff/events}"
SMOKE_ENTERPRISE="${SMOKE_ENTERPRISE:-false}"

request() {
  local path="$1"; shift
  curl -fsS --max-time 15 -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' "$BASE_URL$path" "$@" >/dev/null
}

request /edge-health
request /
request /api/v1/public/events/upcoming
request /robots.txt
request /sitemap.xml
request /sitemap-1.xml

# Verify key edge security headers without logging response bodies.
headers_file="$(mktemp)"
cookie_jar=""
body_file=""
cleanup(){ rm -f "$headers_file" "$cookie_jar" "$body_file"; }
trap cleanup EXIT
curl -fsS --max-time 15 -D "$headers_file" -o /dev/null -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' "$BASE_URL/"
grep -qi '^content-security-policy:' "$headers_file" || { echo "Missing Content-Security-Policy header" >&2; exit 1; }
grep -qi '^x-content-type-options: *nosniff' "$headers_file" || { echo "Missing X-Content-Type-Options: nosniff" >&2; exit 1; }
grep -qi '^x-frame-options:' "$headers_file" || { echo "Missing X-Frame-Options header" >&2; exit 1; }
grep -qi '^strict-transport-security:' "$headers_file" || { echo "Missing Strict-Transport-Security header" >&2; exit 1; }

# Optional privileged read-only synthetic account. No checkout, payment, refund, or state mutation is
# performed in production smoke testing. The credentials should belong to a dedicated low-privilege
# operational account and be supplied from the CI secret store.
if [[ -n "$SMOKE_EMAIL" && -n "$SMOKE_PASSWORD" ]]; then
  cookie_jar="$(mktemp)"
  body_file="$(mktemp)"
  status="$(curl -sS --max-time 15 -o "$body_file" -w '%{http_code}' -c "$cookie_jar" -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' -H 'Content-Type: application/json' -X POST "$BASE_URL/api/v1/auth/login" --data "$(python3 -c 'import json,os; print(json.dumps({"email":os.environ["SMOKE_EMAIL"],"password":os.environ["SMOKE_PASSWORD"]}))')")"
  [[ "$status" == "200" ]] || { echo "Production authenticated smoke login failed: HTTP $status" >&2; exit 1; }
  token="$(python3 - "$body_file" <<'PY'
import json,sys
with open(sys.argv[1],encoding='utf-8') as f: data=json.load(f)
value=data.get('accessToken')
if not value: raise SystemExit('accessToken missing from login response')
print(value)
PY
)"
  curl -fsS --max-time 15 -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' -H "Authorization: Bearer $token" "$BASE_URL/api/v1/auth/password-status" >/dev/null
  curl -fsS --max-time 15 -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' -H "Authorization: Bearer $token" "$BASE_URL$SMOKE_ROLE_ENDPOINT" >/dev/null
  curl -fsS --max-time 15 -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' -b "$cookie_jar" -H 'Content-Type: application/json' -X POST "$BASE_URL/api/v1/auth/logout" >/dev/null || true
fi

# Live-ticket synthetic read checks. In enterprise certification mode this path is mandatory so a
# deployment cannot pass while ticket retrieval/PDF handling is broken. It is always read-only.
if [[ "${SMOKE_ENTERPRISE,,}" == "true" && ( -z "${SMOKE_TICKET_ID:-}" || -z "${SMOKE_TICKET_TOKEN:-}" ) ]]; then
  echo "SMOKE_ENTERPRISE=true requires SMOKE_TICKET_ID and SMOKE_TICKET_TOKEN." >&2
  exit 1
fi
if [[ -n "${SMOKE_TICKET_ID:-}" && -n "${SMOKE_TICKET_TOKEN:-}" ]]; then
  curl -fsS --max-time 15 -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' -H "X-Ticket-Token: $SMOKE_TICKET_TOKEN" "$BASE_URL/api/v1/public/tickets/$SMOKE_TICKET_ID" >/dev/null
  invalid_status="$(curl -sS --max-time 15 -o /dev/null -w '%{http_code}' -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' -H "X-Ticket-Token: ${SMOKE_TICKET_TOKEN}tampered" "$BASE_URL/api/v1/public/tickets/$SMOKE_TICKET_ID")"
  [[ "$invalid_status" == "401" || "$invalid_status" == "403" ]] || { echo "Invalid ticket token was not rejected (HTTP $invalid_status)" >&2; exit 1; }
  if [[ "${SMOKE_TICKET_PDF:-false}" == "true" ]]; then
    curl -fsS --max-time 20 -o /dev/null -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' -H "X-Ticket-Token: $SMOKE_TICKET_TOKEN" "$BASE_URL/api/v1/public/tickets/$SMOKE_TICKET_ID/pdf"
  fi
fi

echo "Production safe smoke tests passed."
