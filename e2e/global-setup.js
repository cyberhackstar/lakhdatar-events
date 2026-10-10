'use strict';
const { provision } = require('./support/provision');

const FIXTURE_VARS = [
  'E2E_ADMIN_BEARER', 'E2E_STAFF_BEARER', 'E2E_STAFF_EMAIL', 'E2E_STAFF_PASSWORD',
  'E2E_EVENT_ID', 'E2E_TICKET_TYPE_ID', 'E2E_CHECKOUT_EMAIL', 'E2E_IDEMPOTENCY_KEY',
  'E2E_TICKET_ID', 'E2E_TICKET_TOKEN', 'E2E_QR_TOKEN', 'E2E_GATE',
];

module.exports = async function globalSetup() {
  if (process.env.E2E_RUN_MUTATIONS !== 'true' || process.env.E2E_AUTO_PROVISION === 'false') return undefined;

  // Legacy fixture mode is all-or-nothing: mixing manually maintained IDs/tokens with
  // newly provisioned resources can produce a dangerous cross-fixture mismatch.
  const suppliedFixtures = FIXTURE_VARS.filter((key) => Boolean(process.env[key]));
  if (suppliedFixtures.length === FIXTURE_VARS.length) {
    console.log('All legacy E2E fixtures were supplied explicitly; skipping auto-provisioning.');
    return undefined;
  }
  if (suppliedFixtures.length > 0) {
    throw new Error(`Partial legacy E2E fixture set detected (${suppliedFixtures.join(', ')}). Clear all fixture variables to enable automatic staging provisioning, or supply the complete fixture set.`);
  }

  const mask = (v) => { if (process.env.GITHUB_ACTIONS === 'true' && v) console.log(`::add-mask::${v}`); };
  const { env, teardown } = await provision({ baseUrl: process.env.E2E_BASE_URL, env: process.env, mask });

  // Fixtures are a coherent generated set; do not mix them with stale per-test values.
  // Workers are forked after globalSetup, so they inherit these values.
  for (const [k, v] of Object.entries(env)) process.env[k] = v;
  return teardown;
};
