#!/usr/bin/env node
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { refreshAccessToken } = require('./refresh-access-token.cjs');

(async () => {
  const originalFetch = global.fetch;
  const requests = [];
  try {
    global.fetch = async (url, init = {}) => {
      const target = new URL(String(url));
      requests.push({ url: target.toString(), method: init.method, body: init.body ? JSON.parse(init.body) : null });
      if (target.pathname === '/api/v1/auth/login') {
        if (requests.at(-1).body.email === 'scanner@example.test') {
          return new Response(JSON.stringify({ accessToken: 'fresh-scanner-token' }), { status: 200, headers: { 'content-type': 'application/json' } });
        }
        return new Response(JSON.stringify({ mfaRequired: true, mfaSetupRequired: false, mfaChallengeToken: 'mock-challenge' }), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      if (target.pathname === '/api/v1/auth/mfa/verify') {
        return new Response(JSON.stringify({ accessToken: 'fresh-admin-token' }), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      return new Response(JSON.stringify({ code: 'NOT_FOUND' }), { status: 404, headers: { 'content-type': 'application/json' } });
    };

    const staffToken = await refreshAccessToken({ role: 'staff', baseUrl: 'https://staging-events.neelastack.com', env: {
      STAFF_EMAIL: 'scanner@example.test', STAFF_PASSWORD: 'local-test-password',
    }});
    assert.equal(staffToken, 'fresh-scanner-token');
    assert.equal(requests.length, 1);
    assert.equal(new URL(requests[0].url).pathname, '/api/v1/auth/login');
    assert.equal(requests[0].body.email, 'scanner@example.test');

    requests.length = 0;
    const localToken = await refreshAccessToken({ role: 'staff', baseUrl: 'http://127.0.0.1:4002', env: {
      STAFF_EMAIL: 'scanner@example.test', STAFF_PASSWORD: 'local-test-password',
    }});
    assert.equal(localToken, 'fresh-scanner-token');
    assert.equal(new URL(requests[0].url).origin, 'http://127.0.0.1:4002', 'loopback credentials must stay on loopback');

    requests.length = 0;
    const adminToken = await refreshAccessToken({ role: 'admin', baseUrl: 'https://staging-events.neelastack.com', env: {
      ADMIN_REFRESH_EMAIL: 'admin@example.test', ADMIN_REFRESH_PASSWORD: 'local-admin-password',
      ADMIN_REFRESH_TOTP_SECRET: 'JBSWY3DPEHPK3PXP',
    }});
    assert.equal(adminToken, 'fresh-admin-token');
    assert.deepEqual(requests.map(r => new URL(r.url).pathname), ['/api/v1/auth/login', '/api/v1/auth/mfa/verify']);
    assert.ok(requests.every(r => new URL(r.url).origin === 'https://staging-events.neelastack.com'));

    await assert.rejects(() => refreshAccessToken({ role: 'staff', baseUrl: 'https://events.neelastack.com', env: {
      STAFF_EMAIL: 'scanner@example.test', STAFF_PASSWORD: 'local-test-password',
    }}), /approved staging or loopback origin/);
    assert.equal(requests.length, 2, 'unsafe target must be rejected before network I/O');

    // Source-level orchestration guard: passwords stay on the host and are not added to k6 env.
    const scriptDir = __dirname;
    const suiteSource = fs.readFileSync(path.join(scriptDir, 'run-suite.sh'), 'utf8');
    const singleSource = fs.readFileSync(path.join(scriptDir, 'run.sh'), 'utf8');
    const autoRunnerSource = fs.readFileSync(path.join(scriptDir, 'auto-runner.cjs'), 'utf8');
    assert.match(suiteSource, /REFRESH_TOKEN_ROLE=staff[\s\S]*refresh-access-token\.cjs/);
    assert.match(suiteSource, /REFRESH_TOKEN_ROLE=admin[\s\S]*refresh-access-token\.cjs/);
    assert.match(singleSource, /REFRESH_TOKEN_ROLE=staff[\s\S]*refresh-access-token\.cjs/);
    assert.match(singleSource, /REFRESH_TOKEN_ROLE=admin[\s\S]*refresh-access-token\.cjs/);
    assert.match(suiteSource, /-e STAFF_BEARER=\"\$staff_bearer\"/);
    assert.match(suiteSource, /-e ADMIN_BEARER=\"\$admin_bearer\"/);
    assert.doesNotMatch(suiteSource, /-e STAFF_PASSWORD=/);
    assert.doesNotMatch(suiteSource, /-e ADMIN_REFRESH_PASSWORD=/);
    assert.doesNotMatch(singleSource, /-e STAFF_PASSWORD=/);
    assert.doesNotMatch(singleSource, /-e ADMIN_REFRESH_PASSWORD=/);
    assert.match(autoRunnerSource, /STAFF_PASSWORD: generated\.E2E_STAFF_PASSWORD/);
    assert.match(autoRunnerSource, /ADMIN_REFRESH_TOTP_SECRET: adminRefresh\.totpSecret/);

    console.log('PASS: JIT scanner/admin token refresh, admin MFA, staging-origin guard, and no-password-to-k6 contract');
  } finally {
    global.fetch = originalFetch;
  }
})().catch(error => { console.error(error && error.stack || error); process.exitCode = 1; });
