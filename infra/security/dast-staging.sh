#!/usr/bin/env bash
set -euo pipefail
: "${BASE_URL:?BASE_URL is required}"
: "${ZAP_IMAGE:?ZAP_IMAGE must be a pinned digest, e.g. ...@sha256:...}"
[[ "$ZAP_IMAGE" == *@sha256:* ]] || { echo 'ZAP_IMAGE must be pinned by digest' >&2; exit 2; }
case "$BASE_URL" in
  https://events.neelastack.com|https://events.neelastack.com/) echo 'Refusing DAST against production' >&2; exit 2;;
esac
REPORT_DIR="${REPORT_DIR:-./security-results}"; mkdir -p "$REPORT_DIR"
docker run --rm --network host -v "$PWD/$REPORT_DIR:/zap/wrk:rw" "$ZAP_IMAGE" zap-baseline.py -t "$BASE_URL" -r /zap/wrk/zap-baseline.html -J /zap/wrk/zap-baseline.json -T 15
