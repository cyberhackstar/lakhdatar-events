'use strict';
const { provision } = require('./support/provision');

const FIXTURE_VARS = [
  'E2E_ADMIN_BEARER', 'E2E_STAFF_BEARER', 'E2E_STAFF_EMAIL', 'E2E_STAFF_PASSWORD',
  'E2E_EVENT_ID', 'E2E_TICKET_TYPE_ID', 'E2E_CHECKOUT_EMAIL', 'E2E_IDEMPOTENCY_KEY',
  'E2E_TICKET_ID', 'E2E_TICKET_TOKEN', 'E2E_QR_TOKEN', 'E2E_GATE',
];

module.exports = async function globalSetup() {
  if (process.env.E2E_RUN_MUTATIONS !== 'true') return undefined;
  if (process.env.E2E_AUTO_PROVISION === 'false') {
    if ((process.env.E2E_ENV || '').toLowerCase() === 'staging') {
      throw new Error('E2E_AUTO_PROVISION=false is forbidden for staging mutation runs; staging fixtures must be provisioned and torn down by the qualification harness.');
    }
    return undefined;
  }

  // Staging never accepts manually maintained fixture IDs, bearer tokens, or QR tokens. Those
  // values can be stale or bound to the wrong event. Local legacy mode is retained only for a
  // complete fixture set; partial fixture sets are always rejected.
  const suppliedFixtures = FIXTURE_VARS.filter((key) => Boolean(process.env[key]));
  if ((process.env.E2E_ENV || '').toLowerCase() === 'staging' && suppliedFixtures.length > 0) {
    throw new Error(`Manual fixture variables are forbidden for staging E2E (${suppliedFixtures.join(', ')}). Remove legacy fixture variables so the runner can create a fresh disposable fixture set.`);
  }
  if (suppliedFixtures.length === FIXTURE_VARS.length && (process.env.E2E_ENV || '').toLowerCase() === 'local') {
    console.log('Complete local legacy fixture set supplied; skipping auto-provisioning.');
    return undefined;
  }
  if (suppliedFixtures.length > 0) {
    throw new Error(`Partial legacy E2E fixture set detected (${suppliedFixtures.join(', ')}). Clear all fixture variables to enable automatic provisioning.`);
  }

  const mask = (v) => { if (process.env.GITHUB_ACTIONS === 'true' && v) console.log(`::add-mask::${v}`); };
  const { env, teardown } = await provision({ baseUrl: process.env.E2E_BASE_URL, env: process.env, mask });

  // Fixtures are a coherent generated set; do not mix them with stale per-test values.
  // Workers are forked after globalSetup, so they inherit these values.
  for (const [k, v] of Object.entries(env)) process.env[k] = v;

  if (process.env.E2E_RUN_PAYMENT_FLOW === 'true') {
    // Ignore complimentary fixture emails sent during setup; only messages delivered by the
    // paid checkout phase may satisfy the payment-email assertion.
    process.env.E2E_PAYMENT_MAIL_SINCE = new Date().toISOString();
    return async function paymentQualificationTeardown() {
      const reportKit = require('./payment-qualification/report.cjs');
      let report;
      try {
        report = require('node:fs').existsSync(reportKit.RESULT_PATH)
          ? JSON.parse(require('node:fs').readFileSync(reportKit.RESULT_PATH, 'utf8'))
          : reportKit.blankReport();
        await teardown();
        const fs = require('node:fs');
        if (Object.prototype.hasOwnProperty.call(report, 'fixtureCleanupStatus')) {
          report.fixtureCleanupStatus = 'PASS';
          report.fixtureCleanupDetail = 'Disposable staging event/team teardown completed.';
          report.updatedAtUtc = new Date().toISOString();
          const failed = (report.blockers || []).length > 0 || report.testStatus === 'FAIL'
            || report.mailboxCleanupStatus === 'FAIL' || report.checks.some((item) => item.status === 'FAIL');
          const complete = report.testStatus === 'PASS' && report.mailboxCleanupStatus === 'PASS'
            && report.checks.every((item) => item.status === 'PASS');
          report.status = failed ? 'FAIL' : complete ? 'PASS' : 'INCOMPLETE';
          fs.writeFileSync(reportKit.RESULT_PATH, JSON.stringify(report, null, 2) + '\n', { mode: 0o600 });
        } else {
          const cleanup = report.checks.find((item) => item.id === 'cleanup');
          if (cleanup) {
            cleanup.status = 'PENDING';
            cleanup.detail = 'Disposable event/team teardown completed; disposable inbox deletion remains.';
          }
          reportKit.writeReport(report);
        }
      } catch (error) {
        const fs = require('node:fs');
        if (report && Object.prototype.hasOwnProperty.call(report, 'fixtureCleanupStatus')) {
          report.fixtureCleanupStatus = 'FAIL';
          report.fixtureCleanupDetail = 'Disposable staging event/team teardown failed; inspect sanitized workflow logs.';
          report.blockers = Array.isArray(report.blockers) ? report.blockers : [];
          report.blockers.push('Disposable staging fixture cleanup failed.');
          report.status = 'FAIL';
          report.updatedAtUtc = new Date().toISOString();
          fs.writeFileSync(reportKit.RESULT_PATH, JSON.stringify(report, null, 2) + '\n', { mode: 0o600 });
        } else {
          const cleanup = report && report.checks.find((item) => item.id === 'cleanup');
          if (cleanup) {
            cleanup.status = 'FAIL';
            cleanup.detail = 'Disposable event/team teardown failed; inspect the sanitized workflow logs.';
            reportKit.writeReport(report);
          }
        }
        throw error;
      }
    };
  }
  return teardown;
};
