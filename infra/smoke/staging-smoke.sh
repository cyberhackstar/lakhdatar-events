#!/usr/bin/env bash
set -Eeuo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:4003}"
PUBLIC_HOST="${PUBLIC_HOST:-staging-events.neelastack.com}"
[[ "$PUBLIC_HOST" == "staging-events.neelastack.com" ]] || { echo "Refusing smoke target outside staging-events.neelastack.com" >&2; exit 2; }

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

actuator_status="$(curl -sk --max-time 15 -o /dev/null -w '%{http_code}' -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' "$BASE_URL/actuator/prometheus")"
[[ "$actuator_status" == "404" ]] || { echo "Public actuator endpoint is reachable: HTTP $actuator_status" >&2; exit 1; }

headers_file="$(mktemp)"
trap 'rm -f "$headers_file"' EXIT
curl -fsS --max-time 15 -D "$headers_file" -o /dev/null -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' "$BASE_URL/"
grep -qi '^content-security-policy:' "$headers_file"
grep -qi '^x-content-type-options: *nosniff' "$headers_file"
grep -qi '^x-frame-options:' "$headers_file"
grep -qi '^strict-transport-security:' "$headers_file"
grep -qi '^x-robots-tag:.*noindex' "$headers_file"

sitemap="$(curl -fsS --max-time 15 -H "Host: $PUBLIC_HOST" -H 'X-Forwarded-Proto: https' "$BASE_URL/sitemap.xml")"
grep -q 'https://staging-events.neelastack.com' <<<"$sitemap"
! grep -q 'https://events.neelastack.com' <<<"$sitemap"

echo "Staging smoke tests passed."
