#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/infra/loadtest/checkout.js"

grep -q 'function safeErrorCode(response)' "$SCRIPT"
grep -q 'logicalDurationMs' "$SCRIPT"
grep -q 'checkout_initial_transport_failures' "$SCRIPT"
grep -q 'checkout_final_failures' "$SCRIPT"
grep -Fq 'http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));' "$SCRIPT"
grep -Fq "checkout_initial_transport_failures: ['rate<0.03']" "$SCRIPT"
grep -Fq "checkout_final_failures: ['rate<0.01']" "$SCRIPT"
if grep -Fq "http_req_failed: ['rate<0.02']" "$SCRIPT"; then
  echo 'Raw attempts must not override the final idempotent logical-checkout outcome gate.' >&2
  exit 1
fi
grep -q 'if (initialTransportFailure)' "$SCRIPT"
grep -q 'const initialTransportFailure = res.status === 0' "$SCRIPT"
[[ "$(grep -Fc 'http.post(url, body' "$SCRIPT")" -eq 2 ]] || { echo 'Checkout probe may contain exactly one initial POST and one bounded retry site.' >&2; exit 1; }
grep -Fq "attempt: 'transport-retry'" "$SCRIPT"
grep -q 'idempotencyKey:' "$SCRIPT"
grep -q 'diagnosticFailures < 3' "$SCRIPT"
grep -q 'response body and credentials omitted' "$SCRIPT"
# Make sure failure diagnostics do not print complete response bodies or request headers.
if grep -Eq 'console\.(log|warn|error).*res\.body|console\.(log|warn|error).*response\.body|console\.(log|warn|error).*res\.request' "$SCRIPT"; then
  echo 'Checkout load diagnostics must not print bodies or request data.' >&2
  exit 1
fi
echo 'PASS: checkout load retries only a no-response transport failure once with the same idempotency key, and diagnostics are bounded and sanitized.'
