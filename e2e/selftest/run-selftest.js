'use strict';
// Deterministic self-tests against a loopback mock; never touches real staging or production.
const assert = require('assert');
const { start } = require('./mock-api');
const { provision, parseEnvFile, paymentProvider, assertSafeTarget } = require('../support/provision');

const quiet = () => {};
const FIXTURES = ['E2E_ADMIN_BEARER', 'E2E_STAFF_BEARER', 'E2E_STAFF_EMAIL', 'E2E_STAFF_PASSWORD', 'E2E_EVENT_ID', 'E2E_TICKET_TYPE_ID',
  'E2E_CHECKOUT_EMAIL', 'E2E_IDEMPOTENCY_KEY', 'E2E_TICKET_ID', 'E2E_TICKET_TOKEN', 'E2E_QR_TOKEN', 'E2E_GATE'];
const cashfree = { CASHFREE_APP_ID: 'sandbox-app', CASHFREE_SECRET_KEY: 'sandbox-secret', CASHFREE_BASE_URL: 'https://sandbox.cashfree.com/pg', DEFAULT_PAYMENT_PROVIDER: 'CASHFREE' };
const admin = { E2E_ADMIN_EMAIL: 'admin@example.test', E2E_ADMIN_PASSWORD: 'Admin-Password-1' };

async function scenario(name, opts, env, fn) {
  const api = await start(opts);
  try { await fn(api, env); console.log(`ok   ${name}`); }
  catch (error) { console.error(`FAIL ${name}: ${error.stack || error.message}`); process.exitCode = 1; }
  finally { await api.close(); }
}

async function fullFlow(api, env) {
  const masked = [];
  const providerEnv = env.E2E_STAGING_PROVISIONING_CONFIG ? {} : cashfree;
  const fullEnv = { E2E_ENV: 'local', GITHUB_RUN_ID: '4242', ...providerEnv, ...env };
  const { env: out, teardown } = await provision({ baseUrl: api.url, env: fullEnv, log: quiet, mask: (value) => masked.push(value) });
  for (const key of FIXTURES) assert.ok(out[key], `missing ${key}`);
  assert.ok(out.E2E_QR_TOKEN.startsWith('LK1.'), 'QR token decoded from the PNG');
  assert.ok(masked.includes(out.E2E_STAFF_PASSWORD) && masked.includes(out.E2E_QR_TOKEN), 'secrets masked');
  assert.strictEqual(out.E2E_STAFF_EMAIL, 'e2e-4242-1-staff@example.test');
  assert.strictEqual(api.users.get(out.E2E_STAFF_EMAIL).mustChangePassword, false, 'staff initial password must be rotated');
  assert.strictEqual(api.users.get('e2e-4242-1-manager@example.test').mustChangePassword, false, 'manager initial password must be rotated');
  const post = (token) => fetch(api.url + '/api/v1/checkin/scan', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ eventId: out.E2E_EVENT_ID, qrToken: out.E2E_QR_TOKEN, gate: out.E2E_GATE }),
  }).then((response) => response.json());
  assert.strictEqual((await post(out.E2E_STAFF_BEARER)).result, 'ACCEPTED');
  assert.strictEqual((await post(out.E2E_STAFF_BEARER)).result, 'ALREADY_USED');
  await teardown();
  assert.strictEqual(api.events.get(out.E2E_EVENT_ID).status, 'CANCELLED', 'event cancelled on teardown');
  assert.strictEqual(api.users.get(out.E2E_STAFF_EMAIL).active, false, 'staff account disabled during teardown');
  assert.strictEqual(api.users.get('e2e-4242-1-manager@example.test').active, false, 'manager account disabled during teardown');
}

(async () => {
  await scenario('MFA off, Cashfree sandbox and disposable fixtures', { mfa: 'off' }, admin, fullFlow);
  await scenario('admin from provisioning config and Razorpay test provider', { mfa: 'off' }, {
    E2E_STAGING_PROVISIONING_CONFIG: [
      'APP_ENV=staging', 'BOOTSTRAP_ADMIN_EMAIL=admin@example.test', 'BOOTSTRAP_ADMIN_PASSWORD="Admin-Password-1"',
      'DEFAULT_PAYMENT_PROVIDER=RAZORPAY', 'RAZORPAY_KEY_ID=rzp_test_testKey123', 'RAZORPAY_KEY_SECRET=test-secret', 'RAZORPAY_WEBHOOK_SECRET=test-webhook',
    ].join('\n'),
  }, async (api, env) => fullFlow(api, env));
  await scenario('MFA on, admin TOTP and disposable manager auto-enrollment', { mfa: 'on' }, {
    ...admin, E2E_ADMIN_TOTP_SECRET: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
  }, fullFlow);
  await scenario('MFA on without admin TOTP fails before provisioning', { mfa: 'on' }, admin, async (api, env) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...env }, log: quiet }), /E2E_ADMIN_TOTP_SECRET/);
    assert.strictEqual(api.events.size, 0, 'nothing created before required credentials are checked');
  });
  await scenario('pre-existing admin without MFA enrollment is never auto-enrolled', { mfa: 'on', adminEnrolled: false }, admin, async (api, env) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...env }, log: quiet }), /will not change an existing admin/i);
    assert.ok(!api.calls.includes('POST /api/v1/auth/mfa/enroll'), 'existing admin MFA is untouched');
  });
  await scenario('missing admin identity fails clearly', { mfa: 'off' }, {}, async (api) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree }, log: quiet }), /No admin identity/);
  });
  await scenario('partial team creation failure cancels event and leaves no created team user', { mfa: 'off', failTeamRole: 'staff' }, admin, async (api, env) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...env }, log: quiet }), /MOCK_TEAM_FAILURE/);
    assert.strictEqual([...api.events.values()][0].status, 'CANCELLED');
    assert.strictEqual([...api.users.values()].filter((u) => u.role === 'STAFF').length, 0);
    assert.strictEqual(api.calls.filter((call) => call === 'POST /api/v1/admin/organizers/lakhdatar-events/team/staff').length, 1, 'non-idempotent team-create POST is never retried');
  });
  await scenario('cleanup failure is surfaced as a qualification failure', { mfa: 'off', failCleanup: true }, admin, async (api, env) => {
    const { teardown } = await provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...env }, log: quiet });
    await assert.rejects(teardown(), /cleanup did not fully succeed/i);
  });

  const parsed = parseEnvFile('export A=1\n# comment\nB=\'x y\'\nC=plain # inline\n');
  assert.deepStrictEqual(parsed, { A: '1', B: 'x y', C: 'plain' });
  assert.strictEqual(paymentProvider({ DEFAULT_PAYMENT_PROVIDER: 'RAZORPAY', RAZORPAY_KEY_ID: 'rzp_test_ab12', RAZORPAY_KEY_SECRET: 's', RAZORPAY_WEBHOOK_SECRET: 'w' }, {}), 'RAZORPAY');
  assert.throws(() => paymentProvider({ CASHFREE_APP_ID: 'id', CASHFREE_SECRET_KEY: 'secret', CASHFREE_BASE_URL: 'https://api.cashfree.com/pg' }, {}), /non-sandbox URL/i);
  assert.throws(() => paymentProvider({ RAZORPAY_KEY_ID: 'rzp_live_bad', RAZORPAY_KEY_SECRET: 's', RAZORPAY_WEBHOOK_SECRET: 'w' }, {}), /non-test key/i);
  assert.throws(() => assertSafeTarget('https://events.neelastack.com', { E2E_ENV: 'staging' }, { APP_ENV: 'staging' }), /Refusing to provision fixtures outside/);
  assert.throws(() => assertSafeTarget('https://staging-events.neelastack.com', { E2E_ENV: 'staging' }, { APP_ENV: 'production' }), /APP_ENV=staging/);
  assert.throws(() => assertSafeTarget('https://staging-events.neelastack.com', { E2E_ENV: 'staging' }, {}), /APP_ENV=staging/);
  if (!process.exitCode) console.log('SELFTEST PASSED');
})();
