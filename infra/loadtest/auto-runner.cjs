#!/usr/bin/env node
'use strict';

// Provision isolated staging-only fixtures with the same hardened API helper as browser E2E.
// Generated credentials remain process-local, are masked in GitHub Actions, and are never written
// to GITHUB_OUTPUT, logs, .env files, or artifacts. The E2E provisioner performs best-effort
// cleanup on partial failure; this wrapper performs and verifies teardown after each successful run.
const path = require('node:path');
const { spawn } = require('node:child_process');
const { provision } = require('../../e2e/support/provision');

const ROOT = path.resolve(__dirname, '../..');
const BASE_URL = process.env.BASE_URL || 'https://staging-events.neelastack.com';
const VALID_BASE_URL = 'https://staging-events.neelastack.com';
const requested = String(process.argv[2] || 'enterprise-gate').trim();
const TESTS_WITH_FIXTURES = new Set([
  'enterprise-gate', 'suite', 'public-event.js', 'checkout.js',
  'checkout-idempotency.js', 'checkin.js', 'ticket-pdf.js', 'operations.js',
]);
const PUBLIC_ONLY_TESTS = new Set(['catalog.js', 'burst.js', 'seo.js', 'thousands.js']);

if (BASE_URL !== VALID_BASE_URL) {
  console.error(`Refusing load qualification target ${BASE_URL}; only ${VALID_BASE_URL} is allowed.`);
  process.exit(2);
}
if (!TESTS_WITH_FIXTURES.has(requested) && !PUBLIC_ONLY_TESTS.has(requested)) {
  console.error(`Unsupported k6 test '${requested}'.`);
  process.exit(2);
}
if (process.env.ALLOW_PRODUCTION_CHECKOUT_LOAD === 'true') {
  console.error('ALLOW_PRODUCTION_CHECKOUT_LOAD must remain false.');
  process.exit(2);
}

const mask = (value) => {
  if (process.env.GITHUB_ACTIONS === 'true' && value) {
    // GitHub commands can have multiline secrets. All generated auth/QR values are single-line.
    console.log(`::add-mask::${String(value).replace(/[\r\n]/g, '')}`);
  }
};

function runBash(args, env) {
  return new Promise((resolve, reject) => {
    const child = spawn('bash', args, { cwd: ROOT, env, stdio: 'inherit' });
    activeChild = child;
    child.once('error', reject);
    child.once('close', (code, signal) => {
      if (activeChild === child) activeChild = null;
      resolve({ code: code === null ? 1 : code, signal });
    });
  });
}

let activeChild = null;
let receivedSignal = null;
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    receivedSignal = signal;
    if (activeChild && !activeChild.killed) activeChild.kill('SIGTERM');
  });
}

async function main() {
  let teardown = null;
  let fixtureTag = '';
  let exitCode = 0;
  try {
    if (TESTS_WITH_FIXTURES.has(requested)) {
      if (!process.env.E2E_STAGING_PROVISIONING_CONFIG) {
        throw new Error('This load scenario needs fresh fixtures. Configure the existing protected staging secret E2E_STAGING_PROVISIONING_CONFIG; individual LOADTEST_* fixture secrets are not used.');
      }
      const provisionEnv = {
        ...process.env,
        E2E_ENV: 'staging',
        E2E_BASE_URL: BASE_URL,
        E2E_FIXTURE_TAG_PREFIX: 'loadtest',
        E2E_FIXTURE_PROFILE: 'loadtest',
      };
      const fixture = await provision({
        baseUrl: BASE_URL,
        env: provisionEnv,
        log: (message) => console.log(message),
        mask,
      });
      teardown = fixture.teardown;
      fixtureTag = fixture.tag;
      const generated = fixture.env;
      for (const value of [generated.E2E_ADMIN_BEARER, generated.E2E_STAFF_BEARER,
        generated.E2E_TICKET_TOKEN, generated.E2E_QR_TOKEN, generated.E2E_IDEMPOTENCY_KEY]) mask(value);

      const loadEnv = {
        ...process.env,
        BASE_URL,
        EVENT_SLUG: fixture.tag,
        EVENT_ID: generated.E2E_EVENT_ID,
        LOADTEST_EVENT_ID: generated.E2E_EVENT_ID,
        ADMIN_EVENT_ID: generated.E2E_EVENT_ID,
        TICKET_TYPE_ID: generated.E2E_TICKET_TYPE_ID,
        TICKET_ID: generated.E2E_TICKET_ID,
        TICKET_TOKEN: generated.E2E_TICKET_TOKEN,
        ADMIN_BEARER: generated.E2E_ADMIN_BEARER,
        STAFF_BEARER: generated.E2E_STAFF_BEARER,
        CHECKIN_QR_TOKENS: generated.E2E_QR_TOKEN,
        GATE: generated.E2E_GATE || 'Main Gate',
        TEST_IDEMPOTENCY_KEY: generated.E2E_IDEMPOTENCY_KEY,
        ENABLE_CHECKOUT_LOAD: 'true',
        ENABLE_ENTERPRISE_CHECKOUT_LOAD: requested === 'enterprise-gate' ? 'true' : 'false',
        ALLOW_PRODUCTION_CHECKOUT_LOAD: 'false',
      };
      // Keep setup/deployment credentials out of k6 subprocesses. Only the verifier below receives
      // the SSH key, and it runs a read-only SQL query inside the isolated staging DB container.
      delete loadEnv.E2E_STAGING_PROVISIONING_CONFIG;
      delete loadEnv.STAGING_DEPLOY_HOST;
      delete loadEnv.STAGING_DEPLOY_SSH_KEY;
      delete loadEnv.STAGING_DEPLOY_KNOWN_HOSTS;
      delete loadEnv.STAGING_ENV_FILE;
      delete loadEnv.DATABASE_URL;
      const bashArgs = requested === 'enterprise-gate'
        ? [path.join('infra', 'loadtest', 'enterprise-gate.sh')]
        : requested === 'suite'
          ? [path.join('infra', 'loadtest', 'run-suite.sh')]
          : [path.join('infra', 'loadtest', 'run.sh'), requested];
      const result = await runBash(bashArgs, loadEnv);
      exitCode = result.code;
      if (result.signal && !receivedSignal) receivedSignal = result.signal;
      // Run invariant verification independently for every fixture-backed scenario, even when
      // k6 exits on a threshold failure. The verifier gets only pinned SSH material and the UUID.
      if (!receivedSignal) {
        const verifyEnv = {
          PATH: process.env.PATH || '',
          HOME: process.env.HOME || '',
          LANG: process.env.LANG || 'C.UTF-8',
          LOADTEST_EVENT_ID: generated.E2E_EVENT_ID,
          STAGING_DEPLOY_HOST: process.env.STAGING_DEPLOY_HOST || '',
          STAGING_DEPLOY_SSH_KEY: process.env.STAGING_DEPLOY_SSH_KEY || '',
          STAGING_DEPLOY_KNOWN_HOSTS: process.env.STAGING_DEPLOY_KNOWN_HOSTS || '',
        };
        const invariants = await runBash([path.join('infra', 'loadtest', 'verify-invariants.sh')], verifyEnv);
        if (invariants.code !== 0) {
          console.error('Post-run database invariants failed; keeping the run failed even if cleanup succeeds.');
          if (exitCode === 0) exitCode = invariants.code;
        }
        if (invariants.signal && !receivedSignal) receivedSignal = invariants.signal;
      }
    } else {
      // Public-only scenarios do not need admin credentials or any fixture mutation.
      const result = await runBash([path.join('infra', 'loadtest', 'run.sh'), requested], {
        ...process.env,
        BASE_URL,
        ALLOW_PRODUCTION_CHECKOUT_LOAD: 'false',
      });
      exitCode = result.code;
      if (result.signal && !receivedSignal) receivedSignal = result.signal;
    }
  } catch (error) {
    console.error(`Load qualification failed: ${error && error.message ? error.message : 'unknown error'}`);
    exitCode = 1;
  } finally {
    if (teardown) {
      try {
        console.log(`Starting verified cleanup for disposable fixture ${fixtureTag}`);
        await teardown();
      } catch (error) {
        console.error(`CRITICAL: fixture teardown failed for ${fixtureTag}: ${error && error.message ? error.message : 'unknown error'}`);
        // A qualification run is not successful if disposable accounts/event could not be cleaned.
        exitCode = 1;
      }
    }
  }
  if (receivedSignal) exitCode = receivedSignal === 'SIGINT' ? 130 : 143;
  process.exitCode = exitCode;
}

main().catch((error) => {
  console.error(`Load qualification orchestration failed: ${error && error.message ? error.message : 'unknown error'}`);
  process.exitCode = 1;
});
