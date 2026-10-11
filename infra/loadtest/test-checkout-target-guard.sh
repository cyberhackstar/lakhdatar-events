#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
function hostnameFromBaseUrl(value) {
  const match = /^https?:\/\/(\[[0-9a-f:.]+\]|[^/:?#@]+)(?::\d+)?(?:[/?#]|$)/i.exec(value);
  assert.ok(match, `Invalid absolute HTTP(S) URL: ${value}`);
  return match[1].replace(/^\[|\]$/g, '').toLowerCase();
}
function isProductionEventsHost(host) {
  return host === 'events.neelastack.com' || host.endsWith('.events.neelastack.com');
}
assert.equal(hostnameFromBaseUrl('https://staging-events.neelastack.com'), 'staging-events.neelastack.com');
assert.equal(isProductionEventsHost(hostnameFromBaseUrl('https://staging-events.neelastack.com')), false);
assert.equal(isProductionEventsHost(hostnameFromBaseUrl('https://events.neelastack.com')), true);
assert.equal(isProductionEventsHost(hostnameFromBaseUrl('https://api.events.neelastack.com:443/path')), true);
assert.equal(hostnameFromBaseUrl('http://127.0.0.1:4002/api'), '127.0.0.1');
assert.throws(() => hostnameFromBaseUrl('https://user@events.neelastack.com'), /Invalid absolute HTTP/);
assert.throws(() => hostnameFromBaseUrl('javascript:alert(1)'), /Invalid absolute HTTP/);
console.log('Checkout load target guard regression tests passed.');
NODE
# The shipped k6 scripts run from stdin, so direct URL usage is a deterministic runtime failure.
if grep -nE 'new[[:space:]]+URL[[:space:]]*\(' "$ROOT/infra/loadtest/checkout.js" "$ROOT/infra/loadtest/checkout-hot-sale.js" "$ROOT/infra/loadtest/checkout-idempotency.js"; then
  echo 'k6 checkout load scripts must not use the URL global.' >&2
  exit 1
fi

# The idempotency scenario must hold both key and request payload constant and stay under the
# minimum per-email limiter; the standard success probe stays under the default client budget.
node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
import fs from 'node:fs';
const idempotency = fs.readFileSync('infra/loadtest/checkout-idempotency.js', 'utf8');
const checkout = fs.readFileSync('infra/loadtest/checkout.js', 'utf8');
const hotSale = fs.readFileSync('infra/loadtest/checkout-hot-sale.js', 'utf8');
assert.match(idempotency, /rate:\s*4,\s*timeUnit:\s*'1s',\s*duration:\s*'1s'/);
assert.match(idempotency, /customerName:\s*'Idempotency Load Test'/);
assert.match(idempotency, /customerEmail:\s*'idempotency-load@example\.invalid'/);
assert.doesNotMatch(idempotency, /customer(?:Name|Email):\s*`[^`]*\$\{__(?:VU|ITER)/);
assert.match(checkout, /rate:\s*1,\s*timeUnit:\s*'6s',\s*duration:\s*'4m'/);
assert.match(hotSale, /http\.setResponseCallback\(http\.expectedStatuses\(200, 201, 409, 429, 503\)\)/);
assert.match(hotSale, /checkout_unavailable_503:\s*\['rate<0\.02'\]/);
assert.match(hotSale, /unavailableResponses\.add\(response\.status === 503\)/);
console.log('Checkout idempotency, rate-budget, and overload-response regression tests passed.');
NODE
