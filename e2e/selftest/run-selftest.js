'use strict';
// Deterministic self-tests against a loopback mock; never touches real staging or production.
const assert = require('assert');
const path = require('path');
const { spawnSync } = require('child_process');
const { start } = require('./mock-api');
const { provision, parseEnvFile, adminCredentials, paymentProvider, assertSafeTarget, validateServerPaymentSafety } = require('../support/provision');

const quiet = () => {};
const FIXTURES = ['E2E_ADMIN_BEARER', 'E2E_STAFF_BEARER', 'E2E_STAFF_EMAIL', 'E2E_STAFF_PASSWORD', 'E2E_EVENT_ID', 'E2E_TICKET_TYPE_ID',
  'E2E_CHECKOUT_EMAIL', 'E2E_IDEMPOTENCY_KEY', 'E2E_TICKET_ID', 'E2E_TICKET_TOKEN', 'E2E_QR_TOKEN', 'E2E_GATE'];
const cashfree = { DEFAULT_PAYMENT_PROVIDER: 'CASHFREE' };
const admin = { E2E_ADMIN_EMAIL: 'admin@example.test' };
const mockAdminEnv = (api, extra = {}) => ({ E2E_ADMIN_EMAIL: api.adminEmail, E2E_ADMIN_PASSWORD: api.adminPassword, ...extra });

async function scenario(name, opts, env, fn) {
  const api = await start(opts);
  try { await fn(api, env); console.log(`ok   ${name}`); }
  catch (error) { console.error(`FAIL ${name}: ${error.stack || error.message}`); process.exitCode = 1; }
  finally { await api.close(); }
}

async function fullFlow(api, env) {
  const masked = [];
  const providerEnv = env.E2E_STAGING_PROVISIONING_CONFIG ? {} : cashfree;
  const mockCredentials = env.E2E_STAGING_PROVISIONING_CONFIG ? {} : mockAdminEnv(api);
  const fullEnv = { E2E_ENV: 'local', GITHUB_RUN_ID: '4242', ...providerEnv, ...mockCredentials, ...env };
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
  const firstTicketResponse = await fetch(`${api.url}/api/v1/public/tickets/${out.E2E_TICKET_ID}`, { headers: { 'X-Ticket-Token': out.E2E_TICKET_TOKEN } });
  assert.strictEqual(firstTicketResponse.status, 200);
  const firstTicket = await firstTicketResponse.json();
  assert.strictEqual(firstTicket.ticketPosition, 1, 'first ticket position is one');
  assert.strictEqual(firstTicket.orderTicketCount, 2, 'ticket order count matches actual order quantity');
  const scannedTicket = [...api.tickets.values()].find((ticket) => ticket.qrToken === out.E2E_QR_TOKEN);
  assert.ok(scannedTicket, 'decoded QR belongs to a provisioned ticket');
  assert.strictEqual(scannedTicket.ticketPosition, 2, 'scanner fixture is the second ticket');
  assert.strictEqual(scannedTicket.orderTicketCount, 2, 'scanner fixture order count is accurate');
  const accepted = await post(out.E2E_STAFF_BEARER);
  assert.strictEqual(accepted.result, 'ACCEPTED');
  assert.strictEqual(accepted.ticketPosition, scannedTicket.ticketPosition);
  assert.strictEqual(accepted.orderTicketCount, scannedTicket.orderTicketCount);
  const duplicate = await post(out.E2E_STAFF_BEARER);
  assert.strictEqual(duplicate.result, 'ALREADY_USED');
  assert.strictEqual(duplicate.ticketPosition, scannedTicket.ticketPosition);
  assert.strictEqual(duplicate.orderTicketCount, scannedTicket.orderTicketCount);
  await teardown();
  assert.strictEqual(api.events.get(out.E2E_EVENT_ID).status, 'CANCELLED', 'event cancelled on teardown');
  assert.strictEqual(api.users.get(out.E2E_STAFF_EMAIL).active, false, 'staff account disabled during teardown');
  assert.strictEqual(api.users.get('e2e-4242-1-manager@example.test').active, false, 'manager account disabled during teardown');
}

(async () => {
  await scenario('MFA off, Cashfree sandbox and disposable fixtures', { mfa: 'off' }, admin, fullFlow);
  await scenario('admin from provisioning config and Razorpay test provider', { mfa: 'off' }, {}, async (api) => {
    const config = [
      'APP_ENV=staging', `E2E_ADMIN_EMAIL=${api.adminEmail}`, `E2E_ADMIN_PASSWORD="${api.adminPassword}"`,
      'DEFAULT_PAYMENT_PROVIDER=RAZORPAY',
    ].join('\n');
    await fullFlow(api, { E2E_STAGING_PROVISIONING_CONFIG: config });
  });
  await scenario('MFA on, generated admin TOTP and disposable manager auto-enrollment', { mfa: 'on' }, admin, async (api, env) => {
    await fullFlow(api, { ...mockAdminEnv(api, env), E2E_ADMIN_TOTP_SECRET: api.adminSecret });
  });
  await scenario('MFA on without admin TOTP fails before provisioning', { mfa: 'on' }, admin, async (api, env) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...mockAdminEnv(api, env) }, log: quiet }), /E2E_ADMIN_TOTP_SECRET/);
    assert.strictEqual(api.events.size, 0, 'nothing created before required credentials are checked');
  });
  await scenario('pre-existing admin without MFA enrollment is never auto-enrolled', { mfa: 'on', adminEnrolled: false }, admin, async (api, env) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...mockAdminEnv(api, env) }, log: quiet }), /will not change an existing admin/i);
    assert.ok(!api.calls.includes('POST /api/v1/auth/mfa/enroll'), 'existing admin MFA is untouched');
  });
  await scenario('missing admin identity fails clearly', { mfa: 'off' }, {}, async (api) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree }, log: quiet }), /No admin identity/);
  });
  await scenario('partial team creation failure cancels event and leaves no created team user', { mfa: 'off', failTeamRole: 'staff' }, admin, async (api, env) => {
    await assert.rejects(provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...mockAdminEnv(api, env) }, log: quiet }), /MOCK_TEAM_FAILURE/);
    assert.strictEqual([...api.events.values()][0].status, 'CANCELLED');
    assert.strictEqual([...api.users.values()].filter((u) => u.role === 'STAFF').length, 0);
    assert.strictEqual(api.calls.filter((call) => call === 'POST /api/v1/admin/organizers/lakhdatar-events/team/staff').length, 1, 'non-idempotent team-create POST is never retried');
  });
  await scenario('cleanup failure is surfaced as a qualification failure', { mfa: 'off', failCleanup: true }, admin, async (api, env) => {
    const { teardown } = await provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...mockAdminEnv(api, env) }, log: quiet });
    await assert.rejects(teardown(), /cleanup did not fully succeed/i);
  });
  await scenario('cleanup can resume after bounded retries are exhausted', { mfa: 'off', failCleanupCount: 3 }, admin, async (api, env) => {
    const { teardown, tag } = await provision({ baseUrl: api.url, env: { E2E_ENV: 'local', ...cashfree, ...mockAdminEnv(api, env) }, log: quiet });
    await assert.rejects(teardown(), /cleanup did not fully succeed/i);
    await teardown();
    const event = [...api.events.values()].find((item) => item.slug === tag);
    assert.strictEqual(event.status, 'CANCELLED');
    assert.strictEqual(api.users.get('admin@example.test').active, true, 'cleanup never deactivates the privileged admin');
  });

  const parsed = parseEnvFile('export A=1\n# comment\nB=\'x y\'\nC=plain # inline\n');
  assert.deepStrictEqual({ ...parsed }, { A: '1', B: 'x y', C: 'plain' });
  assert.throws(() => require('../support/provision').adminCredentials({ E2E_STAGING_PROVISIONING_CONFIG: 'APP_ENV=staging\nDB_PASSWORD=not-allowed' }), /unapproved key/);
  assert.throws(() => parseEnvFile('APP_ENV=staging\nAPP_ENV=production'), /duplicate key APP_ENV/);
  assert.throws(() => parseEnvFile('this is not KEY=value'), /malformed line/);
  assert.strictEqual(validateServerPaymentSafety({ environment: 'staging', safeForE2E: true, cashfreeMode: 'sandbox' }, 'CASHFREE', 'staging'), true);
  assert.strictEqual(validateServerPaymentSafety({ environment: 'staging', safeForE2E: true, razorpayMode: 'test' }, 'RAZORPAY', 'staging'), true);
  assert.throws(() => validateServerPaymentSafety({ environment: 'staging', safeForE2E: true, cashfreeMode: 'unsafe' }, 'CASHFREE', 'staging'), /requires 'sandbox'/);
  assert.throws(() => validateServerPaymentSafety({ environment: 'production', safeForE2E: true, cashfreeMode: 'sandbox' }, 'CASHFREE', 'staging'), /preflight failed/);
  assert.strictEqual(paymentProvider({ APP_ENV: 'staging', DEFAULT_PAYMENT_PROVIDER: 'RAZORPAY' }, {}), 'RAZORPAY', 'E2E config selects provider without carrying gateway credentials');
  assert.strictEqual(paymentProvider({ APP_ENV: 'staging', DEFAULT_PAYMENT_PROVIDER: 'CASHFREE' }, {}), 'CASHFREE', 'server preflight is responsible for validating its actual provider mode');
  assert.throws(() => paymentProvider({ APP_ENV: 'staging' }, {}), /Set DEFAULT_PAYMENT_PROVIDER/);
  assert.throws(() => adminCredentials({ E2E_STAGING_PROVISIONING_CONFIG: 'APP_ENV=staging\nDEFAULT_PAYMENT_PROVIDER=CASHFREE\nCASHFREE_APP_ID=TEST_PLACEHOLDER_NOT_A_CREDENTIAL' }), /unapproved key/);
  assert.throws(() => adminCredentials({ E2E_STAGING_PROVISIONING_CONFIG: 'APP_ENV=staging\nDB_PASSWORD=must-not-be-here' }), /unapproved key/);
  assert.throws(() => assertSafeTarget('https://events.neelastack.com', { E2E_ENV: 'staging' }, { APP_ENV: 'staging' }), /Refusing to provision fixtures outside/);
  assert.throws(() => assertSafeTarget('https://staging-events.neelastack.com', { E2E_ENV: 'staging' }, { APP_ENV: 'production' }), /APP_ENV=staging/);
  assert.throws(() => assertSafeTarget('https://staging-events.neelastack.com', { E2E_ENV: 'staging' }, {}), /APP_ENV=staging/);

  // Exercise global-setup guards in a subprocess so environment isolation is realistic and the
  // self-test process's own configuration cannot influence the result.
  const globalSetupScript = "require('./global-setup')().then(() => process.exit(0)).catch((error) => { console.error(error.message); process.exit(1); });";
  const globalSetupEnv = { ...process.env };
  for (const key of FIXTURES) delete globalSetupEnv[key];
  globalSetupEnv.E2E_RUN_MUTATIONS = 'true';
  globalSetupEnv.E2E_ENV = 'staging';
  globalSetupEnv.E2E_AUTO_PROVISION = 'true';
  delete globalSetupEnv.E2E_BASE_URL;
  const staleFixtureProbe = spawnSync(process.execPath, ['-e', globalSetupScript], {
    cwd: path.resolve(__dirname, '..'),
    env: { ...globalSetupEnv, E2E_STAFF_BEARER: 'stale-test-token' },
    encoding: 'utf8',
  });
  assert.notStrictEqual(staleFixtureProbe.status, 0, 'staging global setup refuses stale manually supplied fixture values');
  assert.match(`${staleFixtureProbe.stdout || ''}${staleFixtureProbe.stderr || ''}`, /Manual fixture variables are forbidden for staging E2E/);

  const disabledProvisionProbe = spawnSync(process.execPath, ['-e', globalSetupScript], {
    cwd: path.resolve(__dirname, '..'),
    env: { ...globalSetupEnv, E2E_AUTO_PROVISION: 'false' },
    encoding: 'utf8',
  });
  assert.notStrictEqual(disabledProvisionProbe.status, 0, 'staging global setup cannot silently disable provisioning');
  assert.match(`${disabledProvisionProbe.stdout || ''}${disabledProvisionProbe.stderr || ''}`, /E2E_AUTO_PROVISION=false is forbidden for staging/);

  if (!process.exitCode) console.log('SELFTEST PASSED');
})();
