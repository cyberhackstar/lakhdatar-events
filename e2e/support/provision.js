'use strict';
/**
 * Provision disposable fixtures through the authenticated v2 API for staging E2E qualification.
 * Never writes to the database directly, never enrolls a pre-existing admin in MFA, never sends
 * payment requests to a live gateway, and never replays a non-idempotent write after a 5xx.
 */
const crypto = require('crypto');
const { totp } = require('./totp');

const GATE = 'Main Gate';
const STAGING_ORIGIN = 'https://staging-events.neelastack.com';
class ProvisionError extends Error {}

const ALLOWED_CONFIG_KEYS = new Set([
  // Minimal identity-only allowlist. Payment credentials, deployment topology, database, JWT,
  // mail, and application runtime settings must never enter the browser-test secret.
  'APP_ENV', 'E2E_ADMIN_EMAIL', 'E2E_ADMIN_PASSWORD', 'E2E_ADMIN_TOTP_SECRET',
  'E2E_ORGANIZER_SLUG', 'DEFAULT_PAYMENT_PROVIDER',
]);

function parseEnvFile(text) {
  const out = Object.create(null);
  for (const raw of String(text || '').replace(/\r/g, '').split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const m = /^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line);
    if (!m) throw new ProvisionError('E2E_STAGING_PROVISIONING_CONFIG contains a malformed line; use KEY=value entries only');
    const key = m[1];
    if (Object.prototype.hasOwnProperty.call(out, key)) throw new ProvisionError(`E2E_STAGING_PROVISIONING_CONFIG contains duplicate key ${key}`);
    let v = m[2].trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    else v = v.replace(/\s+#.*$/, '').trim();
    out[key] = v;
  }
  return out;
}

function validateProvisioningConfig(file) {
  const unexpected = Object.keys(file).filter((key) => !ALLOWED_CONFIG_KEYS.has(key));
  if (unexpected.length) {
    throw new ProvisionError(`E2E_STAGING_PROVISIONING_CONFIG contains unapproved key(s): ${unexpected.join(', ')}. Do not include database, JWT-signing, SMTP, deployment or other runtime secrets.`);
  }
}

function adminCredentials(env) {
  const file = parseEnvFile(env.E2E_STAGING_PROVISIONING_CONFIG);
  validateProvisioningConfig(file);
  return {
    file,
    email: env.E2E_ADMIN_EMAIL || file.E2E_ADMIN_EMAIL || '',
    password: env.E2E_ADMIN_PASSWORD || file.E2E_ADMIN_PASSWORD || '',
    totpSecret: env.E2E_ADMIN_TOTP_SECRET || file.E2E_ADMIN_TOTP_SECRET || '',
    organizerSlug: env.E2E_ORGANIZER_SLUG || file.E2E_ORGANIZER_SLUG || '',
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
  if (String(file.APP_ENV || env.APP_ENV || '').trim().toLowerCase() === 'production') {
    throw new ProvisionError('E2E_STAGING_PROVISIONING_CONFIG contains APP_ENV=production; refusing automatic E2E provisioning');
  }
  if (!['RAZORPAY', 'CASHFREE'].includes(explicit)) {
    throw new ProvisionError('Set DEFAULT_PAYMENT_PROVIDER to RAZORPAY or CASHFREE in E2E_STAGING_PROVISIONING_CONFIG. Gateway credentials belong only in the protected staging deployment environment.');
  }
  return explicit;
}

function validateServerPaymentSafety(report, provider, environment = 'staging') {
  if (!report || report.environment !== environment || report.safeForE2E !== true) {
    throw new ProvisionError('Staging backend payment-safety preflight failed or was unavailable. No fixtures should be created until the deployed backend confirms sandbox-only payment configuration.');
  }
  const expectedMode = provider === 'CASHFREE' ? 'sandbox' : provider === 'RAZORPAY' ? 'test' : '';
  const actualMode = provider === 'CASHFREE' ? report.cashfreeMode : report.razorpayMode;
  if (!expectedMode || actualMode !== expectedMode) {
    throw new ProvisionError(`Staging backend reports ${provider} mode '${actualMode || 'unknown'}'; E2E requires '${expectedMode || 'unsupported'}'. No fixtures have been created.`);
  }
  return true;
}

function makeClient(baseUrl, log) {
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  async function api(method, path, { token, body, headers = {}, ok = [200, 201, 204], retrySafe = false, retryOnTransient = false } = {}) {
    const h = { Accept: 'application/json', ...headers };
    if (body !== undefined) h['Content-Type'] = 'application/json';
    if (token) h.Authorization = `Bearer ${token}`;
    const safeToRetry = ['GET', 'HEAD', 'OPTIONS'].includes(method)
      || (retrySafe && ['DELETE', 'PUT', 'PATCH'].includes(method))
      || retryOnTransient;
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
        log(`${method} ${path} network error; retrying transient request (${attempt}/2)`);
        await sleep(500 * attempt);
        continue;
      }
      if (![429, 502, 503, 504].includes(res.status) || !safeToRetry || attempt === 3) {
        responseTimeout = timeout;
        break;
      }
      clearTimeout(timeout);
      log(`${method} ${path} -> ${res.status}, retrying transient request (${attempt}/2)`);
      await sleep(750 * attempt);
    }
    let responseText;
    try { responseText = await res.text(); }
    catch (error) { throw new ProvisionError(`${method} ${path} response body read failed: ${error.message}`); }
    finally { clearTimeout(responseTimeout); }
    let json = null;
    try { json = responseText ? JSON.parse(responseText) : null; } catch { }
    if (!ok.includes(res.status)) {
      const code = json && typeof json.code === 'string' && /^[A-Za-z0-9_-]{1,80}$/.test(json.code) ? json.code : '';
      const detail = code ? ` (${code})` : '';
      const err = new ProvisionError(`${method} ${path} failed: HTTP ${res.status}${detail}`);
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
  const retryTransient = async (operation, run) => {
    for (let attempt = 1; attempt <= 3; attempt++) {
      try {
        return await run(attempt);
      } catch (error) {
        if (attempt === 3 || ![429, 502, 503, 504].includes(error && error.status)) throw error;
        console.log(`${label} ${operation} hit transient HTTP ${error.status}; retrying (${attempt}/2)`);
        await sleep(750 * attempt);
      }
    }
    throw new ProvisionError(`${label} ${operation} failed after transient retries`);
  };

  let first = (await retryTransient('login', async () => (await api('POST', '/api/v1/auth/login', {
    body: { email, password }, retryOnTransient: true,
  })).json || {}));
  if (first.accessToken && !first.mfaRequired) return { token: first.accessToken };
  if (!first.mfaRequired) throw new ProvisionError(`${label} login returned no access token`);

  let challengeToken = first.mfaChallengeToken;
  if (first.mfaSetupRequired) {
    if (!allowEnroll) {
      throw new ProvisionError(`${label} account requires MFA enrollment. Enroll this existing account interactively; automated E2E will not change an existing admin's MFA settings.`);
    }
    const enroll = (await retryTransient('MFA-enroll', async () => (await api('POST', '/api/v1/auth/mfa/enroll', {
      body: { challengeToken }, retryOnTransient: true,
    })).json || {}));
    if (!enroll.secret) throw new ProvisionError(`${label} MFA enrollment returned no TOTP secret`);
    const done = (await retryTransient('MFA-confirm', async () => (await api('POST', '/api/v1/auth/mfa/confirm', {
      body: { challengeToken, code: totp(enroll.secret) }, retryOnTransient: true,
    })).json || {}));
    if (!done.accessToken) throw new ProvisionError(`${label} MFA enrollment was not confirmed`);
    return { token: done.accessToken, totpSecret: enroll.secret };
  }
  if (!totpSecret) {
    throw new ProvisionError(`${label} account has MFA enabled but the TOTP key is not available to CI. Supply E2E_ADMIN_TOTP_SECRET in E2E_STAGING_PROVISIONING_CONFIG. Do not disable privileged MFA.`);
  }

  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const verified = (await retryTransient('MFA-verify', async () => (await api('POST', '/api/v1/auth/mfa/verify', {
        body: { challengeToken, code: totp(totpSecret) }, retryOnTransient: true,
      })).json || {}));
      if (!verified.accessToken) throw new ProvisionError(`${label} MFA verification returned no access token`);
      return { token: verified.accessToken };
    } catch (error) {
      if (attempt === 2 || ![400, 401, 429].includes(error.status)) throw error;
      await sleep(30_500 - (Date.now() % 30_000) + 500);
      first = (await retryTransient('refresh-login', async () => (await api('POST', '/api/v1/auth/login', {
        body: { email, password }, retryOnTransient: true,
      })).json || {}));
      if (!first.mfaRequired || first.mfaSetupRequired || !first.mfaChallengeToken) {
        throw new ProvisionError(`${label} MFA challenge could not be refreshed; check account MFA state`);
      }
      challengeToken = first.mfaChallengeToken;
    }
  }
  throw new ProvisionError(`${label} MFA verification failed`);
}

const rnd = (n) => crypto.randomBytes(n).toString('base64url');
const strongPassword = () => `E2e-${rnd(18)}-9a!`;
const isUuid = (v) => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);

async function provision({ baseUrl, env = process.env, log = console.log, mask = () => {} }) {
  const creds = adminCredentials(env);
  for (const value of [creds.email, creds.password, creds.totpSecret]) {
    if (value) mask(value);
  }
  assertSafeTarget(baseUrl, env, creds.file);
  const provider = paymentProvider(creds.file, env);
  const client = makeClient(baseUrl, log);
  const { api } = client;
  const runId = (env.GITHUB_RUN_ID
    ? `${env.GITHUB_RUN_ID}-${env.GITHUB_RUN_ATTEMPT || 1}`
    : `${Date.now().toString(36)}-${rnd(3)}`).toLowerCase().replace(/[^a-z0-9-]/g, '');
  const tagPrefix = String(env.E2E_FIXTURE_TAG_PREFIX || 'e2e').toLowerCase();
  if (!/^[a-z][a-z0-9-]{1,15}$/.test(tagPrefix)) throw new ProvisionError('E2E_FIXTURE_TAG_PREFIX must be a short lowercase slug prefix');
  const tag = `${tagPrefix}-${runId}`;
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
  let cleanupComplete = false;
  let cleanupPromise = null;

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
      if (error.status !== 401 || !relogin) throw error;
      adminToken = await relogin();
      const replaySafe = ['GET', 'HEAD', 'OPTIONS'].includes(method)
        || (options.retrySafe === true && ['DELETE', 'PUT', 'PATCH'].includes(method));
      if (!replaySafe) {
        const guarded = new ProvisionError(`${method} ${path} returned HTTP 401; the admin session was refreshed, but the potentially mutating request was not replayed. Re-run after checking fixture state.`);
        guarded.status = 401;
        throw guarded;
      }
      return api(method, path, { ...options, token: adminToken });
    }
  }

  try {
    adminToken = env.E2E_ADMIN_BEARER ? String(env.E2E_ADMIN_BEARER).replace(/^Bearer\s+/i, '').trim() : '';
    if (!adminToken) {
      if (!creds.email || !creds.password) {
        throw new ProvisionError('No admin identity available. Supply E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD in E2E_STAGING_PROVISIONING_CONFIG. Fixture variables are generated automatically.');
      }
      relogin = async () => (await login(client, creds.email, creds.password, { totpSecret: creds.totpSecret, label: 'Admin' })).token;
      log('Provisioning: signing in with the staging admin identity');
      adminToken = await relogin();
    } else {
      relogin = creds.email && creds.password
        ? async () => (await login(client, creds.email, creds.password, { totpSecret: creds.totpSecret, label: 'Admin' })).token
        : null;
    }
    secret('E2E_ADMIN_BEARER', adminToken);

    let paymentSafety;
    try {
      paymentSafety = (await adminCall('GET', '/api/v1/admin/qualification/payment-safety')).json || {};
    } catch (error) {
      if (env.E2E_ENV === 'staging') {
        throw new ProvisionError(`Staging payment-safety preflight is missing or failed (${error.status || 'network/error'}). Deploy the backend with the staging payment-safety endpoint and sandbox-safe payment settings before running E2E.`);
      }
      throw error;
    }
    validateServerPaymentSafety(paymentSafety, provider, env.E2E_ENV === 'staging' ? 'staging' : 'local');

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

    const loadTestProfile = String(env.E2E_FIXTURE_PROFILE || '').toLowerCase() === 'loadtest';
    const fixtureCapacity = loadTestProfile ? 50000 : 500;
    const fixtureTicketQuantity = loadTestProfile ? 50000 : 200;
    const startsAt = new Date(Date.now() + 10 * 60_000);
    const endsAt = new Date(startsAt.getTime() + 4 * 3_600_000);

    const created = (await adminCall('POST', '/api/v1/admin/events', {
      body: {
        organizerSlug: organizer.slug,
        slug: tag,
        name: `Enterprise E2E ${runId}`,
        shortDescription: 'Disposable enterprise E2E event',
        description: 'Created through the normal staging API for automated qualification.',
        category: 'Test',
        timezone: 'Asia/Kolkata',
        startsAt: startsAt.toISOString(),
        endsAt: endsAt.toISOString(),
        capacity: fixtureCapacity,
        venueName: 'Automated Qualification Venue',
        venueAddress: 'Staging only',
        city: 'Jaipur',
        state: 'Rajasthan',
        paymentProvider: provider,
        ticketTypes: [{
          name: loadTestProfile ? 'Load Test General' : 'E2E General',
          description: 'Disposable sandbox qualification ticket',
          priceMinorUnits: 10_000,
          totalQuantity: fixtureTicketQuantity,
          minPerOrder: 1,
          maxPerOrder: 10,
        }],
      },
    })).json || {};

    eventId = created.id || created.eventId || '';
    if (!isUuid(eventId)) throw new ProvisionError('Event creation response did not contain a valid event ID');
    out.E2E_EVENT_ID = eventId;
    out.E2E_EVENT_SLUG = tag;

    const view = (await adminCall('GET', `/api/v1/admin/events/${eventId}`)).json || {};
    const ticketTypes = view.ticketTypes || view.tickets || [];
    const ticketTypeId = ticketTypes[0] && ticketTypes[0].id;
    if (!isUuid(ticketTypeId)) throw new ProvisionError('Created event did not return a valid ticket type ID');
    out.E2E_TICKET_TYPE_ID = ticketTypeId;

    const readiness = (await adminCall('GET', `/api/v1/admin/events/${eventId}/publish-readiness`)).json || {};
    if (readiness.ready === false) throw new ProvisionError(`E2E event cannot be published: ${(readiness.blockers || []).join('; ')}`);
    await adminCall('POST', `/api/v1/admin/events/${eventId}/publish`, { ok: [200, 204] });

    log(`Provisioning complete: ${tag} (${provider} sandbox); disposable fixture values are ready`);
    return { env: out, teardown: async () => undefined, tag };
  } catch (error) {
    throw error;
  }
}

module.exports = { provision, ProvisionError, parseEnvFile, adminCredentials, paymentProvider, assertSafeTarget, validateServerPaymentSafety, makeClient, login };
