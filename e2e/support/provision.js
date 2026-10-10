'use strict';
/**
 * Provision disposable fixtures through the authenticated v2 API for staging E2E qualification.
 * Never writes to the database directly, never enrolls a pre-existing admin in MFA, never sends
 * payment requests to a live gateway, and never replays a non-idempotent write after a 5xx.
 */
const crypto = require('crypto');
const { PNG } = require('pngjs');
const jsQR = require('jsqr');
const { totp } = require('./totp');

const GATE = 'Main Gate';
const STAGING_ORIGIN = 'https://staging-events.neelastack.com';
class ProvisionError extends Error {}

function parseEnvFile(text) {
  const out = {};
  for (const raw of String(text || '').replace(/\r/g, '').split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const m = /^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line);
    if (!m) continue;
    let v = m[2].trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    // dotenv-style inline comments are only stripped from unquoted values.
    else v = v.replace(/\s+#.*$/, '').trim();
    out[m[1]] = v;
  }
  return out;
}

function adminCredentials(env) {
  const file = parseEnvFile(env.E2E_STAGING_PROVISIONING_CONFIG);
  return {
    file,
    email: env.E2E_ADMIN_EMAIL || file.E2E_ADMIN_EMAIL || file.BOOTSTRAP_ADMIN_EMAIL || '',
    password: env.E2E_ADMIN_PASSWORD || file.E2E_ADMIN_PASSWORD || file.BOOTSTRAP_ADMIN_PASSWORD || '',
    totpSecret: env.E2E_ADMIN_TOTP_SECRET || file.E2E_ADMIN_TOTP_SECRET || '',
    organizerSlug: env.E2E_ORGANIZER_SLUG || file.E2E_ORGANIZER_SLUG || file.BOOTSTRAP_ORGANIZER_SLUG || '',
  };
}

function assertSafeTarget(baseUrl, env, file = {}) {
  if (!baseUrl) throw new ProvisionError('E2E_BASE_URL is required for automatic fixture provisioning');
  let parsed;
  try { parsed = new URL(baseUrl); } catch { throw new ProvisionError('E2E_BASE_URL must be a valid absolute URL'); }
  if (env.E2E_ENV === 'staging') {
    if (parsed.origin !== STAGING_ORIGIN || parsed.protocol !== 'https:' || parsed.pathname !== '/' || parsed.search || parsed.hash || parsed.username || parsed.password) {
      throw new ProvisionError(`Refusing to provision fixtures outside ${STAGING_ORIGIN}`);
    }
    const appEnv = String(file.APP_ENV || '').trim().toLowerCase();
    if (appEnv !== 'staging') {
      throw new ProvisionError('E2E_ENV=staging requires E2E_STAGING_PROVISIONING_CONFIG to identify APP_ENV=staging; refusing an unverified environment');
    }
    if (String(file.PRODUCTION_TOPOLOGY || '').toLowerCase() === 'enterprise-ha') {
      throw new ProvisionError('Refusing to provision staging E2E fixtures because E2E_STAGING_PROVISIONING_CONFIG identifies a production topology');
    }
  } else if (env.E2E_ENV === 'local') {
    const loopback = ['127.0.0.1', 'localhost', '[::1]'].includes(parsed.hostname);
    if (!loopback || !['http:', 'https:'].includes(parsed.protocol)) {
      throw new ProvisionError('Local automatic provisioning is restricted to localhost/127.0.0.1');
    }
  } else {
    throw new ProvisionError('Automatic fixture provisioning requires E2E_ENV=staging or E2E_ENV=local; refusing an unclassified target');
  }
}

function paymentProvider(file, env) {
  const explicit = String(env.DEFAULT_PAYMENT_PROVIDER || file.DEFAULT_PAYMENT_PROVIDER || '').trim().toUpperCase();
  if (explicit && !['RAZORPAY', 'CASHFREE'].includes(explicit)) {
    throw new ProvisionError('DEFAULT_PAYMENT_PROVIDER in E2E_STAGING_PROVISIONING_CONFIG must be RAZORPAY or CASHFREE');
  }
  const cashId = file.CASHFREE_APP_ID || env.CASHFREE_APP_ID || '';
  const cashSecret = file.CASHFREE_SECRET_KEY || env.CASHFREE_SECRET_KEY || '';
  const cashUrl = file.CASHFREE_BASE_URL || env.CASHFREE_BASE_URL || '';
  const cashUrlParsed = (() => { try { return new URL(cashUrl); } catch { return null; } })();
  const cashHost = cashUrlParsed ? cashUrlParsed.hostname.toLowerCase() : '';
  const cashCredsPresent = Boolean(cashId || cashSecret);
  const cashfreeSafe = Boolean(cashId && cashSecret && cashUrlParsed && cashUrlParsed.protocol === 'https:' && cashHost === 'sandbox.cashfree.com' && cashUrlParsed.pathname.replace(/\/$/, '') === '/pg' && !cashUrlParsed.search && !cashUrlParsed.hash && !cashUrlParsed.username && !cashUrlParsed.password);

  const razorId = file.RAZORPAY_KEY_ID || env.RAZORPAY_KEY_ID || '';
  const razorSecret = file.RAZORPAY_KEY_SECRET || env.RAZORPAY_KEY_SECRET || '';
  const razorWebhook = file.RAZORPAY_WEBHOOK_SECRET || env.RAZORPAY_WEBHOOK_SECRET || '';
  const razorCredsPresent = Boolean(razorId || razorSecret || razorWebhook);
  const razorpaySafe = Boolean(/^rzp_test_[A-Za-z0-9]+$/.test(razorId) && razorSecret && razorWebhook);

  if (String(file.APP_ENV || env.APP_ENV || '').toLowerCase() === 'production') {
    throw new ProvisionError('E2E_STAGING_PROVISIONING_CONFIG contains APP_ENV=production; refusing automatic E2E provisioning');
  }
  if (cashCredsPresent && !cashfreeSafe) {
    throw new ProvisionError('Cashfree is configured with incomplete credentials or a non-sandbox URL. Staging E2E requires CASHFREE_BASE_URL on sandbox.cashfree.com.');
  }
  if (razorCredsPresent && !razorpaySafe) {
    throw new ProvisionError('Razorpay is configured with incomplete credentials or a non-test key. Staging E2E requires a complete rzp_test_* credential set.');
  }

  if (explicit === 'RAZORPAY' && razorpaySafe) return 'RAZORPAY';
  if (explicit === 'CASHFREE' && cashfreeSafe) return 'CASHFREE';
  if (explicit && ((explicit === 'RAZORPAY' && razorCredsPresent) || (explicit === 'CASHFREE' && cashCredsPresent))) {
    throw new ProvisionError(`Configured ${explicit} provider is not safe for staging E2E checkout`);
  }
  if (razorpaySafe) return 'RAZORPAY';
  if (cashfreeSafe) return 'CASHFREE';
  throw new ProvisionError('No payment sandbox is available for checkout E2E. Configure either a complete Razorpay rzp_test_* credential set or Cashfree sandbox credentials in E2E_STAGING_PROVISIONING_CONFIG.');
}

function makeClient(baseUrl, log) {
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  async function api(method, path, { token, body, headers = {}, ok = [200, 201, 204], retrySafe = false } = {}) {
    const h = { Accept: 'application/json', ...headers };
    if (body !== undefined) h['Content-Type'] = 'application/json';
    if (token) h.Authorization = `Bearer ${token}`;
    const safeToRetry = method === 'GET' || retrySafe;
    let res;
    let responseTimeout;
    for (let attempt = 1; attempt <= (safeToRetry ? 3 : 1); attempt++) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 20_000);
      try {
        res = await fetch(new URL(path, baseUrl), {
          method, headers: h, body: body === undefined ? undefined : JSON.stringify(body),
          signal: controller.signal,
        });
      } catch (error) {
        clearTimeout(timeout);
        if (!safeToRetry || attempt === (safeToRetry ? 3 : 1)) {
          throw new ProvisionError(`${method} ${path} network failure${error && error.name === 'AbortError' ? ' (20s timeout)' : ''}; request was not replayed unless safe: ${error.message}`);
        }
        log(`${method} ${path} network error; retrying safe request (${attempt}/2)`);
        await sleep(500 * attempt);
        continue;
      }
      if (![429, 502, 503, 504].includes(res.status) || !safeToRetry || attempt === 3) {
        responseTimeout = timeout; // includes response-body read in the request timeout
        break;
      }
      clearTimeout(timeout);
      log(`${method} ${path} -> ${res.status}, retrying safe request (${attempt}/2)`);
      await sleep(750 * attempt);
    }
    let responseText;
    try { responseText = await res.text(); }
    catch (error) { throw new ProvisionError(`${method} ${path} response body read failed: ${error.message}`); }
    finally { clearTimeout(responseTimeout); }
    let json = null;
    try { json = responseText ? JSON.parse(responseText) : null; } catch { /* error below includes a short body */ }
    if (!ok.includes(res.status)) {
      const detail = json && (json.message || json.code || json.error)
        ? `${json.code || ''} ${json.message || json.error || ''}`.trim() : responseText.slice(0, 220);
      const err = new ProvisionError(`${method} ${path} failed: HTTP ${res.status}${detail ? ` — ${detail}` : ''}`);
      err.status = res.status;
      err.code = json && json.code;
      throw err;
    }
    return { status: res.status, json, headers: res.headers };
  }
  return { api, sleep };
}

async function login(client, email, password, { totpSecret = '', allowEnroll = false, label = 'User' } = {}) {
  const { api, sleep } = client;
  let first = (await api('POST', '/api/v1/auth/login', { body: { email, password } })).json || {};
  if (first.accessToken && !first.mfaRequired) return { token: first.accessToken };
  if (!first.mfaRequired) throw new ProvisionError(`${label} login returned no access token`);

  let challengeToken = first.mfaChallengeToken;
  if (first.mfaSetupRequired) {
    if (!allowEnroll) {
      throw new ProvisionError(`${label} account requires MFA enrollment. Enroll this existing account interactively; automated E2E will not change an existing admin's MFA settings.`);
    }
    const enroll = (await api('POST', '/api/v1/auth/mfa/enroll', { body: { challengeToken } })).json || {};
    if (!enroll.secret) throw new ProvisionError(`${label} MFA enrollment returned no TOTP secret`);
    const done = (await api('POST', '/api/v1/auth/mfa/confirm', { body: { challengeToken, code: totp(enroll.secret) } })).json || {};
    if (!done.accessToken) throw new ProvisionError(`${label} MFA enrollment was not confirmed`);
    return { token: done.accessToken, totpSecret: enroll.secret };
  }
  if (!totpSecret) {
    throw new ProvisionError(`${label} account has MFA enabled but the TOTP key is not available to CI. Supply E2E_ADMIN_TOTP_SECRET or E2E_ADMIN_TOTP_SECRET in E2E_STAGING_PROVISIONING_CONFIG. Do not disable privileged MFA to make tests pass.`);
  }

  // TOTP codes are single-use in a time window. If a request is rejected, wait for the next window
  // and obtain a fresh challenge instead of resubmitting a stale challenge token.
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const verified = (await api('POST', '/api/v1/auth/mfa/verify', { body: { challengeToken, code: totp(totpSecret) } })).json || {};
      if (!verified.accessToken) throw new ProvisionError(`${label} MFA verification returned no access token`);
      return { token: verified.accessToken };
    } catch (error) {
      if (attempt === 2 || ![400, 401, 429].includes(error.status)) throw error;
      await sleep(30_500 - (Date.now() % 30_000) + 500);
      first = (await api('POST', '/api/v1/auth/login', { body: { email, password } })).json || {};
      if (!first.mfaRequired || first.mfaSetupRequired || !first.mfaChallengeToken) {
        throw new ProvisionError(`${label} MFA challenge could not be refreshed; check account MFA state`);
      }
      challengeToken = first.mfaChallengeToken;
    }
  }
  throw new ProvisionError(`${label} MFA verification failed`);
}

function decodeQrDataUri(dataUri) {
  const m = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(dataUri || '');
  if (!m) throw new ProvisionError('Ticket view did not include a base64 PNG QR code');
  let png;
  try { png = PNG.sync.read(Buffer.from(m[1], 'base64')); } catch (e) { throw new ProvisionError(`Ticket QR PNG could not be parsed: ${e.message}`); }
  const result = jsQR(new Uint8ClampedArray(png.data), png.width, png.height);
  if (!result || !result.data) throw new ProvisionError('Could not decode the QR code returned for the issued ticket');
  return result.data;
}

const rnd = (n) => crypto.randomBytes(n).toString('base64url');
const strongPassword = () => `E2e-${rnd(18)}-9a!`;
const isUuid = (v) => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);

async function provision({ baseUrl, env = process.env, log = console.log, mask = () => {} }) {
  const creds = adminCredentials(env);
  assertSafeTarget(baseUrl, env, creds.file);
  const provider = paymentProvider(creds.file, env);
  const client = makeClient(baseUrl, log);
  const { api } = client;
  const runId = (env.GITHUB_RUN_ID
    ? `${env.GITHUB_RUN_ID}-${env.GITHUB_RUN_ATTEMPT || 1}`
    : `${Date.now().toString(36)}-${rnd(3)}`).toLowerCase().replace(/[^a-z0-9-]/g, '');
  const tag = `e2e-${runId}`;
  const staffEmail = `${tag}-staff@example.test`;
  const managerEmail = `${tag}-manager@example.test`;
  const checkoutEmail = `${tag}@example.test`;
  const staffInitialPassword = strongPassword();
  const managerInitialPassword = strongPassword();
  const staffFinalPassword = strongPassword();
  const managerFinalPassword = strongPassword();
  const out = {};
  const secret = (key, value) => { mask(value); out[key] = value; return value; };
  let adminToken = '';
  let relogin = null;
  let organizer = null;
  let eventId = '';
  let staffUserId = '';
  let managerUserId = '';
  let manager = null;
  let staff = null;
  let cleanupStarted = false;

  async function currentAdminToken() {
    if (adminToken) return adminToken;
    if (!relogin) throw new ProvisionError('No admin token available for cleanup');
    adminToken = await relogin();
    return adminToken;
  }
  async function adminCall(method, path, options = {}) {
    const token = await currentAdminToken();
    try { return await api(method, path, { ...options, token }); }
    catch (error) {
      // Retry only after an explicit 401 (authorization failed before the handler ran). Do not replay
      // writes after 5xx/network failures, since those may have committed on the server.
      if (error.status !== 401 || !relogin) throw error;
      adminToken = await relogin();
      return api(method, path, { ...options, token: adminToken });
    }
  }

  async function discoverPartialResources() {
    if (!adminToken && !relogin) return;
    try {
      if (!organizer) {
        const list = (await adminCall('GET', '/api/v1/admin/organizers')).json || {};
        const organizers = Array.isArray(list) ? list : list.organizers || [];
        organizer = creds.organizerSlug
          ? organizers.find((item) => item.slug === creds.organizerSlug)
          : organizers[0];
      }
      if (!eventId) {
        const list = (await adminCall('GET', '/api/v1/admin/events')).json || [];
        const events = Array.isArray(list) ? list : list.events || [];
        const found = events.find((item) => item.slug === tag || item.publicSlug === tag);
        if (found && found.id) eventId = found.id;
      }
      if (organizer && (!staffUserId || !managerUserId)) {
        const team = (await adminCall('GET', `/api/v1/admin/organizers/${encodeURIComponent(organizer.slug)}/team`)).json || {};
        const findMember = (list, email) => (Array.isArray(list) ? list : []).find((item) => String(item.email || '').toLowerCase() === email.toLowerCase());
        const staffMember = findMember(team.staff, staffEmail);
        const managerMember = findMember(team.managers, managerEmail);
        if (!staffUserId && staffMember) staffUserId = staffMember.id;
        if (!managerUserId && managerMember) managerUserId = managerMember.id;
      }
    } catch (error) {
      throw new ProvisionError(`resource discovery failed: ${error.message}`);
    }
  }

  async function cleanup({ failOnError = true } = {}) {
    if (cleanupStarted) return;
    cleanupStarted = true;
    const failures = [];
    try { await discoverPartialResources(); } catch (e) { failures.push(`resource discovery: ${e.message}`); }

    // Remove event assignments before cancellation, then revoke disposable team accounts.
    if (eventId && staffEmail && staffUserId) {
      try { await adminCall('DELETE', `/api/v1/admin/events/${eventId}/staff?email=${encodeURIComponent(staffEmail)}`, { retrySafe: true }); }
      catch (e) { if (e.status !== 404) failures.push(`staff unassignment: ${e.message}`); }
    }
    if (eventId && managerEmail && managerUserId) {
      try { await adminCall('DELETE', `/api/v1/admin/events/${eventId}/managers?email=${encodeURIComponent(managerEmail)}`, { retrySafe: true }); }
      catch (e) { if (e.status !== 404) failures.push(`manager unassignment: ${e.message}`); }
    }
    if (organizer && staffUserId) {
      try { await adminCall('PATCH', `/api/v1/admin/organizers/${encodeURIComponent(organizer.slug)}/team/${staffUserId}`, { body: { active: false }, retrySafe: true }); }
      catch (e) { if (e.status !== 404) failures.push(`staff deactivation: ${e.message}`); }
    }
    if (organizer && managerUserId) {
      try { await adminCall('PATCH', `/api/v1/admin/organizers/${encodeURIComponent(organizer.slug)}/team/${managerUserId}`, { body: { active: false }, retrySafe: true }); }
      catch (e) { if (e.status !== 404) failures.push(`manager deactivation: ${e.message}`); }
    }
    if (eventId) {
      try { await adminCall('POST', `/api/v1/admin/events/${eventId}/cancel`, { ok: [200, 204], retrySafe: true }); }
      catch (e) { if (e.status !== 404 && e.status !== 409) failures.push(`event cancellation: ${e.message}`); }
    }
    if (failures.length) {
      const msg = `E2E fixture cleanup did not fully succeed for ${tag}: ${failures.join(' | ')}`;
      log(msg);
      if (failOnError) throw new ProvisionError(msg);
    } else log(`Teardown complete: event ${tag} cancelled and disposable team identities deactivated`);
  }

  try {
    // 1. Admin identity. A pre-existing admin is never automatically enrolled in MFA.
    adminToken = env.E2E_ADMIN_BEARER ? String(env.E2E_ADMIN_BEARER).replace(/^Bearer\s+/i, '').trim() : '';
    if (!adminToken) {
      if (!creds.email || !creds.password) {
        throw new ProvisionError('No admin identity available. Supply E2E_ADMIN_EMAIL/PASSWORD or use BOOTSTRAP_ADMIN_EMAIL/PASSWORD from E2E_STAGING_PROVISIONING_CONFIG. Fixture variables are generated automatically.');
      }
      relogin = async () => (await login(client, creds.email, creds.password, { totpSecret: creds.totpSecret, label: 'Admin' })).token;
      log('Provisioning: signing in with the staging admin identity');
      adminToken = await relogin();
    } else {
      // A bearer-only identity can provision, but cannot be renewed if it expires during teardown.
      relogin = creds.email && creds.password
        ? async () => (await login(client, creds.email, creds.password, { totpSecret: creds.totpSecret, label: 'Admin' })).token
        : null;
    }
    secret('E2E_ADMIN_BEARER', adminToken);

    // 2. Find the correct organizer; an explicit slug must exist rather than silently selecting another.
    const orgList = (await adminCall('GET', '/api/v1/admin/organizers')).json || {};
    const organizers = Array.isArray(orgList) ? orgList : orgList.organizers || [];
    organizer = creds.organizerSlug
      ? organizers.find((item) => item.slug === creds.organizerSlug)
      : organizers.length === 1 ? organizers[0] : null;
    if (!organizer) {
      if (creds.organizerSlug) throw new ProvisionError(`Configured E2E organizer '${creds.organizerSlug}' does not exist`);
      if (organizers.length === 0) throw new ProvisionError('Staging has no organizer; create one before running the E2E suite');
      throw new ProvisionError('Staging has multiple organizers; set E2E_ORGANIZER_SLUG in E2E_STAGING_PROVISIONING_CONFIG so fixtures are created under the intended organizer');
    }

    // 3. Create one future-dated sandbox event with one ticket type.
    const startsAt = new Date(Date.now() + 30 * 86_400_000);
    const endsAt = new Date(startsAt.getTime() + 4 * 3_600_000);
    const created = (await adminCall('POST', '/api/v1/admin/events', {
      body: {
        organizerSlug: organizer.slug, slug: tag, name: `Enterprise E2E ${runId}`,
        shortDescription: 'Disposable enterprise E2E event', description: 'Created through the normal staging API for automated qualification.',
        category: 'Test', timezone: 'Asia/Kolkata', startsAt: startsAt.toISOString(), endsAt: endsAt.toISOString(),
        capacity: 500, venueName: 'E2E Venue', venueAddress: 'Staging', city: 'Jaipur', state: 'Rajasthan', paymentProvider: provider,
        ticketTypes: [{ name: 'E2E General', description: 'Disposable sandbox test ticket', priceMinorUnits: 10_000, totalQuantity: 200, minPerOrder: 1, maxPerOrder: 5, saleStartsAt: null, saleEndsAt: null }],
      },
    })).json || {};
    eventId = created.id || created.eventId || '';
    if (!isUuid(eventId)) throw new ProvisionError('Event creation response did not contain a valid event ID');
    out.E2E_EVENT_ID = eventId;

    const view = (await adminCall('GET', `/api/v1/admin/events/${eventId}`)).json || {};
    const ticketTypes = view.ticketTypes || view.tickets || [];
    const ticketTypeId = ticketTypes[0] && ticketTypes[0].id;
    if (!isUuid(ticketTypeId)) throw new ProvisionError('Created event did not return a valid ticket type ID');
    out.E2E_TICKET_TYPE_ID = ticketTypeId;

    const readiness = (await adminCall('GET', `/api/v1/admin/events/${eventId}/publish-readiness`)).json || {};
    if (readiness.ready === false) throw new ProvisionError(`E2E event cannot be published: ${(readiness.blockers || []).join('; ')}`);
    await adminCall('POST', `/api/v1/admin/events/${eventId}/publish`, { ok: [200, 204] });

    // 4. Create accounts using current organizer-team routes, then clear mustChangePassword via auth API.
    const staffCreated = (await adminCall('POST', `/api/v1/admin/organizers/${encodeURIComponent(organizer.slug)}/team/staff`, {
      body: { email: staffEmail, name: 'E2E Scanner Staff', password: staffInitialPassword },
    })).json || {};
    staffUserId = staffCreated.member && staffCreated.member.id;
    if (!isUuid(staffUserId)) throw new ProvisionError('Staff creation did not return member.id');
    await adminCall('POST', `/api/v1/admin/events/${eventId}/staff`, { body: { email: staffEmail, gate: GATE }, ok: [200, 201, 204] });
    const staffFirstLogin = await login(client, staffEmail, staffInitialPassword, { label: 'Staff' });
    const staffPasswordChange = await apiForUser(client, staffFirstLogin.token, staffInitialPassword, staffFinalPassword);
    staff = { token: staffPasswordChange.accessToken || staffFirstLogin.token };
    if (!staff.token) throw new ProvisionError('Staff password rotation returned no access token');
    out.E2E_STAFF_EMAIL = staffEmail;
    secret('E2E_STAFF_PASSWORD', staffFinalPassword);
    secret('E2E_STAFF_BEARER', staff.token);
    out.E2E_GATE = GATE;

    const managerCreated = (await adminCall('POST', `/api/v1/admin/organizers/${encodeURIComponent(organizer.slug)}/team/managers`, {
      body: { email: managerEmail, name: 'E2E Event Manager', password: managerInitialPassword },
    })).json || {};
    managerUserId = managerCreated.member && managerCreated.member.id;
    if (!isUuid(managerUserId)) throw new ProvisionError('Manager creation did not return member.id');
    await adminCall('POST', `/api/v1/admin/events/${eventId}/managers`, { body: { email: managerEmail }, ok: [200, 201, 204] });
    const managerFirstLogin = await login(client, managerEmail, managerInitialPassword, { label: 'Disposable manager', allowEnroll: true });
    const managerPasswordChange = await apiForUser(client, managerFirstLogin.token, managerInitialPassword, managerFinalPassword);
    manager = { token: managerPasswordChange.accessToken || managerFirstLogin.token };
    if (!manager.token) throw new ProvisionError('Manager password rotation returned no access token');
    mask(managerInitialPassword); mask(managerFinalPassword); mask(manager.token);

    const issued = (await api('POST', '/api/v1/admin/manager-tickets/complimentary', {
      token: manager.token,
      body: { eventId, ticketTypeId, quantity: 2, attendeeName: 'Enterprise E2E', attendeeEmail: checkoutEmail,
        attendeePhone: '+919999999999', idempotencyKey: crypto.randomUUID() },
    })).json || {};
    const tickets = issued.tickets || [];
    if (tickets.length < 2 || !isUuid(tickets[0].ticketId) || !tickets[0].accessToken || !tickets[1].ticketId || !tickets[1].accessToken) {
      throw new ProvisionError('Complimentary ticket issuance did not return two complete ticket credentials');
    }
    out.E2E_CHECKOUT_EMAIL = env.E2E_CHECKOUT_EMAIL || checkoutEmail;
    out.E2E_TICKET_ID = tickets[0].ticketId;
    secret('E2E_TICKET_TOKEN', tickets[0].accessToken);
    const scanView = (await api('GET', `/api/v1/public/tickets/${tickets[1].ticketId}`, {
      headers: { 'X-Ticket-Token': tickets[1].accessToken },
    })).json || {};
    secret('E2E_QR_TOKEN', decodeQrDataUri(scanView.qrDataUri));
    out.E2E_IDEMPOTENCY_KEY = crypto.randomUUID();
    log(`Provisioning complete: ${tag} (${provider} sandbox); fixture values are now available to Playwright workers`);
    return { env: out, teardown: () => cleanup({ failOnError: true }), tag };
  } catch (error) {
    try { await cleanup({ failOnError: false }); }
    catch (cleanupError) { log(`Best-effort cleanup encountered an error: ${cleanupError.message}`); }
    throw error;
  }
}

async function apiForUser(client, token, currentPassword, newPassword) {
  const { api } = client;
  const result = (await api('POST', '/api/v1/auth/change-password', {
    token,
    body: { currentPassword, newPassword },
  })).json || {};
  return result;
}

module.exports = { provision, ProvisionError, parseEnvFile, adminCredentials, decodeQrDataUri, paymentProvider, assertSafeTarget };
