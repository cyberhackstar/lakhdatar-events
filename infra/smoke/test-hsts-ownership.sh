#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"
cat > "$TMP/bin/curl" <<'MOCK_CURL'
#!/usr/bin/env bash
set -Eeuo pipefail
headers_file=""
output_file=""
write_out=""
url=""
while (($#)); do
  case "$1" in
    -D) headers_file="$2"; shift 2 ;;
    -o) output_file="$2"; shift 2 ;;
    -w) write_out="$2"; shift 2 ;;
    -H|-X|--max-time|-c|-b|--data|-d) shift 2 ;;
    -fsS|-fsSI|-f|-s|-S|-k|-sk|-sS|-sSk|-sSf|--fail|--silent|--show-error) shift ;;
    --connect-timeout|--retry|--retry-delay) shift 2 ;;
    *)
      if [[ "$1" == http://* || "$1" == https://* ]]; then url="$1"; fi
      shift
      ;;
  esac
done
[[ -n "$url" ]] || { echo 'mock curl did not receive a URL' >&2; exit 91; }
path="${url#*://}"
path="/${path#*/}"
path="/${path#\?*}"
status=200
[[ "$url" == */actuator/prometheus ]] && status=404
if [[ -n "$headers_file" ]]; then
  {
    printf 'HTTP/1.1 %s Mock\r\n' "$status"
    printf 'Content-Security-Policy: default-src '\''self'\''\r\n'
    printf 'X-Content-Type-Options: nosniff\r\n'
    printf 'X-Frame-Options: DENY\r\n'
    printf 'X-Robots-Tag: noindex\r\n'
    if [[ "$url" == https://* ]]; then
      count="${MOCK_PUBLIC_HSTS_COUNT:-1}"
      for ((i=0; i<count; i++)); do printf 'Strict-Transport-Security: max-age=31536000; includeSubDomains\r\n'; done
    else
      count="${MOCK_ORIGIN_HSTS_COUNT:-1}"
      for ((i=0; i<count; i++)); do printf 'Strict-Transport-Security: max-age=31536000\r\n'; done
    fi
    printf '\r\n'
  } > "$headers_file"
fi
if [[ -n "$output_file" && "$output_file" != /dev/null ]]; then
  if [[ "$path" == / ]]; then printf '<html><title>Mock</title></html>' > "$output_file"; else : > "$output_file"; fi
fi
if [[ "$write_out" == '%{http_code}' ]]; then printf '%s' "$status"; elif [[ -z "$output_file" ]]; then
  if [[ "$url" == */sitemap.xml || "$url" == */sitemap-1.xml ]]; then
    printf '<urlset><loc>https://staging-events.neelastack.com/events</loc></urlset>'
  elif [[ "$url" == */robots.txt ]]; then
    printf 'Sitemap: https://staging-events.neelastack.com/sitemap.xml\n'
  elif [[ "$url" == */ ]]; then printf '<html><title>Mock</title></html>'; fi
fi
if [[ "$status" -ge 400 && "$*" == *'--fail'* ]]; then exit 22; fi
exit 0
MOCK_CURL
chmod +x "$TMP/bin/curl"
export PATH="$TMP/bin:$PATH"

# The origin edge must emit exactly one HSTS header, even on direct HTTP smoke calls.
MOCK_ORIGIN_HSTS_COUNT=1 MOCK_PUBLIC_HSTS_COUNT=1 BASE_URL=http://127.0.0.1:4003 PUBLIC_HOST=staging-events.neelastack.com \
  bash "$ROOT/infra/smoke/staging-smoke.sh" >/dev/null
MOCK_ORIGIN_HSTS_COUNT=1 BASE_URL=http://127.0.0.1:4002 PUBLIC_HOST=events.neelastack.com \
  bash "$ROOT/infra/smoke/production-smoke.sh" >/dev/null
if MOCK_ORIGIN_HSTS_COUNT=0 BASE_URL=http://127.0.0.1:4003 PUBLIC_HOST=staging-events.neelastack.com \
  bash "$ROOT/infra/smoke/staging-smoke.sh" >/dev/null 2>&1; then
  echo 'Expected missing origin HSTS to fail the staging smoke test' >&2
  exit 1
fi
# Public HTTPS must pass with one HSTS header and fail closed with duplicates.
MOCK_PUBLIC_HSTS_COUNT=1 BASE_URL=https://events.neelastack.com PUBLIC_HOST=events.neelastack.com \
  bash "$ROOT/infra/smoke/production-smoke.sh" >/dev/null
if MOCK_PUBLIC_HSTS_COUNT=2 BASE_URL=https://events.neelastack.com PUBLIC_HOST=events.neelastack.com \
  bash "$ROOT/infra/smoke/production-smoke.sh" >/dev/null 2>&1; then
  echo 'Expected duplicate public HSTS to fail the smoke test' >&2
  exit 1
fi
# Public staging workflow must explicitly gate on a single HSTS response.
grep -q 'hsts_count.*== "1"' "$ROOT/.github/workflows/staging.yml"
# The edge is the HSTS owner and must hide application/SSR copies. Two cache locations
# have their own add_header directives, so they each need an explicit HSTS line.
for conf in edge/nginx.conf infra/ha/nginx-ha.conf.example; do
  hsts_directive_count="$(grep -Eic '^[[:space:]]*add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;' "$ROOT/$conf" || true)"
  [[ "$hsts_directive_count" == "3" ]] || { echo "$conf must set HSTS at server level and both cache locations; found $hsts_directive_count directives" >&2; exit 1; }
  grep -Eiq '^[[:space:]]*proxy_hide_header Strict-Transport-Security;' "$ROOT/$conf" || { echo "$conf must hide upstream HSTS headers" >&2; exit 1; }
done
echo 'Single-owner HSTS smoke regression tests passed.'
