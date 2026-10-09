#!/usr/bin/env bash
set -euo pipefail
: "${BASE_URL:?BASE_URL is required}"
ZAP_IMAGE="${ZAP_IMAGE:-zaproxy/zap-stable:2.17.0@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef}"
[[ "$ZAP_IMAGE" == *@sha256:* ]] || { echo 'ZAP_IMAGE must be pinned by digest' >&2; exit 2; }
[[ "$BASE_URL" == 'https://staging-events.neelastack.com' ]] || { echo 'DAST staging target must be https://staging-events.neelastack.com' >&2; exit 2; }
REPORT_DIR="${REPORT_DIR:-./security-results}"; mkdir -p "$REPORT_DIR"
docker run --rm --network host -v "$PWD/$REPORT_DIR:/zap/wrk:rw" "$ZAP_IMAGE" zap-baseline.py -t "$BASE_URL" -r /zap/wrk/zap-baseline.html -J /zap/wrk/zap-baseline.json -T 15
