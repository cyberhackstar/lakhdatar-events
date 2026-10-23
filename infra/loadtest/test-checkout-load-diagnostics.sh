#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/infra/loadtest/checkout.js"

grep -q 'function safeErrorCode(response)' "$SCRIPT"
grep -q 'res\.timings\.duration' "$SCRIPT"
grep -q 'diagnosticFailures < 3' "$SCRIPT"
grep -q 'response body and credentials omitted' "$SCRIPT"
# Make sure failure diagnostics do not print complete response bodies or request headers.
if grep -Eq 'console\.(log|error).*res\.body|console\.(log|error).*response\.body|console\.(log|error).*res\.request' "$SCRIPT"; then
  echo 'Checkout load diagnostics must not print bodies or request data.' >&2
  exit 1
fi
echo 'PASS: checkout load failure diagnostics expose only a bounded safe error code and timing.'
