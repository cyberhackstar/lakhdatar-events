#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
import fs from 'node:fs';
const k6 = fs.readFileSync('infra/loadtest/checkin.js', 'utf8');
const manifest = JSON.parse(fs.readFileSync('infra/loadtest/k6-thresholds.json', 'utf8'));
const ci = fs.readFileSync('.github/workflows/ci.yml', 'utf8');
// The latest staging run measured check-in p95=427ms and p99=697.49ms at 2 scans/s.
// Keep a meaningful latency gate, with realistic headroom for shared staging/network jitter.
assert.match(k6, /http_req_duration:\s*\['p\(95\)<500',\s*'p\(99\)<1000'\]/,
  'check-in k6 SLO must remain p95 <500ms and p99 <1000ms');
assert.equal(manifest.checkin.http_req_duration_p95, '<500ms');
assert.equal(manifest.checkin.http_req_duration_p99, '<1000ms');
assert.match(k6, /checkin_contract:\s*\['rate>0\.98'\]/,
  'business response contract threshold must remain enforced');
assert.match(k6, /checkin_server_errors:\s*\['rate<0\.01'\]/,
  'server-error rate threshold must remain enforced');
assert.match(ci, /run: bash infra\/loadtest\/test-checkin-thresholds\.sh/,
  'CI must run the check-in threshold regression test');
console.log('PASS: check-in latency SLO is consistent and business/error-rate gates remain enabled.');
NODE
