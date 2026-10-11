#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = (p) => fs.readFileSync(p, 'utf8');
const pdf = read('infra/loadtest/ticket-pdf.js');
const operations = read('infra/loadtest/operations.js');
const suite = read('infra/loadtest/run-suite.sh');
const single = read('infra/loadtest/run.sh');
const gate = read('infra/loadtest/enterprise-gate.sh');
const workflow = read('.github/workflows/load-test.yml');
const releaseWorkflow = read('.github/workflows/enterprise-release-qualification.yml');
const edge = read('edge/nginx.conf');

// PDFs must stay below the intentional 20 r/s shared API limit without changing the service SLO.
assert.match(edge, /limit_req_zone\s+\$client_rate_key\s+zone=public_api:10m\s+rate=20r\/s;/);
assert.match(edge, /limit_req\s+zone=public_api\s+burst=40\s+nodelay;/);
assert.match(pdf, /const PDF_RATE\s*=\s*Number\(__ENV\.PDF_RATE\s*\|\|\s*15\)/);
assert.match(pdf, /PDF_RATE < 1 \|\| PDF_RATE > 18/);
assert.match(pdf, /rate:\s*PDF_RATE/);
assert.match(pdf, /http_req_failed:\s*\['rate<0\.01'\]/);
assert.match(pdf, /pdf download is 200/);
assert.match(pdf, /application\/pdf/);
assert.doesNotMatch(pdf, /Number\(__ENV\.RATE\s*\|\|/);

// Operations sends four requests through the shared limiter per iteration; cap it at 4 iter/s = 16 r/s.
assert.match(operations, /const OPERATIONS_RATE\s*=\s*Number\(__ENV\.OPERATIONS_RATE\s*\|\|\s*4\)/);
assert.match(operations, /OPERATIONS_RATE < 1 \|\| OPERATIONS_RATE > 4/);
assert.match(operations, /rate:\s*OPERATIONS_RATE/);
assert.match(operations, /http_req_failed:\s*\['rate<0\.01'\]/);
assert.doesNotMatch(operations, /Number\(__ENV\.RATE\s*\|\|/);
const rateLimitedOperationsGets = (operations.match(/http\.get\(`\$\{BASE_URL\}\/api\/v1\/(?:admin\/events\/|finance\/)/g) || []).length;
assert.equal(rateLimitedOperationsGets, 4, 'rate cap must account for all four shared-limiter operations endpoints');

for (const [name, source] of [['suite', suite], ['single runner', single]]) {
  assert.match(source, /-e PDF_RATE="\$\{PDF_RATE:-15\}"/ , `${name} must pass PDF_RATE independently`);
  assert.match(source, /-e OPERATIONS_RATE="\$\{OPERATIONS_RATE:-4\}"/, `${name} must pass OPERATIONS_RATE independently`);
}
assert.match(gate, /PDF_RATE > 18/);
assert.match(gate, /OPERATIONS_RATE > 4/);
assert.match(workflow, /LOADTEST_PDF_RATE \|\| '15'/);
assert.match(workflow, /LOADTEST_OPERATIONS_RATE \|\| '4'/);
assert.match(releaseWorkflow, /LOADTEST_PDF_RATE \|\| '15'/);
assert.match(releaseWorkflow, /LOADTEST_OPERATIONS_RATE \|\| '4'/);
console.log('PASS: PDF and operations rates are isolated from generic RATE; security rate caps and strict 200/PDF contract remain enforced.');
NODE
