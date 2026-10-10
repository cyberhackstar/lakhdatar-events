#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${BASE_URL:?BASE_URL is required}"
ZAP_IMAGE="${ZAP_IMAGE:-zaproxy/zap-stable:2.17.0@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef}"
[[ "$ZAP_IMAGE" == *@sha256:* ]] || { echo 'ZAP_IMAGE must be pinned by digest' >&2; exit 2; }
[[ "$BASE_URL" == 'https://staging-events.neelastack.com' ]] || { echo 'DAST staging target must be https://staging-events.neelastack.com' >&2; exit 2; }

REPORT_DIR="${REPORT_DIR:-./security-results}"
mkdir -p "$REPORT_DIR"
REPORT_ABS="$(cd "$REPORT_DIR" && pwd)"
# Official ZAP images run as an unprivileged user. GitHub-hosted workspace directories
# are owned by the runner UID, so make only this ephemeral report mount writable for ZAP.
ORIGINAL_REPORT_MODE="$(stat -c '%a' "$REPORT_ABS")"
restore_report_permissions() {
  chmod "$ORIGINAL_REPORT_MODE" "$REPORT_ABS" 2>/dev/null || true
}
trap restore_report_permissions EXIT
chmod 0777 "$REPORT_ABS"

# zap-baseline.py resolves report paths relative to /zap/wrk. Supplying an absolute path
# caused it to look for /zap/wrk/zap/wrk/zap-baseline.html and exit before report upload.
docker run --rm --network host \
  -v "$REPORT_ABS:/zap/wrk:rw" \
  -v "$SCRIPT_DIR/zap-baseline.conf:/zap/wrk/zap-baseline.conf:ro" \
  "$ZAP_IMAGE" zap-baseline.py \
  -t "$BASE_URL" \
  -c zap-baseline.conf \
  -r zap-baseline.html \
  -J zap-baseline.json \
  -T 15
