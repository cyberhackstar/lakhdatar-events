'use strict';
const fs = require('node:fs');
const path = require('node:path');
const RESULT_PATH = path.resolve(__dirname, '../payment-flow-result.json');
const CHECKS = [
  ['sandbox_payment', 'A Cashfree sandbox payment reaches CONFIRMED'],
  ['exactly_one_ticket', 'Exactly one ticket is issued for the paid order'],
  ['order_number_display', 'Payment result displays the Neelastack order number'],
  ['transaction_id_display', 'Payment result displays the verified provider transaction ID'],
  ['ticket_email_references', 'Ticket email contains the order number and provider transaction ID'],
  ['same_email_recovery', 'Ticket recovery succeeds with the same checkout email'],
  ['replay_safety', 'Repeated verification is idempotent and anonymous replay exposes no ticket credentials'],
  ['ledger_reconciliation', 'Exactly one matching SALE ledger entry reconciles to the paid order'],
];
function blankReport() {
  return {
    schemaVersion: 1,
    kind: 'staging-cashfree-payment-flow-qualification',
    generatedAtUtc: new Date().toISOString(),
    workflowSha: process.env.GITHUB_SHA || null,
    status: 'BLOCKED',
    checks: CHECKS.map(([id, title]) => ({ id, title, status: 'BLOCKED', detail: 'Not run; blocked by an earlier step or unavailable prerequisite.' })),
    safety: { target: 'https://staging-events.neelastack.com', provider: 'CASHFREE', providerMode: 'sandbox', credentialsIncluded: false, customerDataIncluded: false, ticketCredentialsIncluded: false },
  };
}
function writeReport(report) {
  report.generatedAtUtc = new Date().toISOString();
  report.status = report.checks.every((c) => c.status === 'PASS') ? 'PASS' : report.checks.some((c) => c.status === 'FAIL') ? 'FAIL' : 'BLOCKED';
  fs.mkdirSync(path.dirname(RESULT_PATH), { recursive: true });
  const tmp = RESULT_PATH + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(report, null, 2) + '\n', { mode: 0o600 });
  fs.renameSync(tmp, RESULT_PATH);
  return report;
}
function init() { writeReport(blankReport()); }
function summary() {
  let report;
  try { report = JSON.parse(fs.readFileSync(RESULT_PATH, 'utf8')); } catch { report = blankReport(); }
  const lines = ['## Staging Cashfree payment-flow qualification', '', '**Overall result: ' + (report.status || 'BLOCKED') + '**', '', '| # | Check | Result | Evidence / failure detail |', '|---:|---|---|---|'];
  for (const [i, item] of (report.checks || []).entries()) {
    const detail = String(item.detail || '').replace(/\|/g, '\\|').replace(/[\r\n]+/g, ' ').slice(0, 240);
    const label = item.title || item.name || item.id || 'Unnamed check';
    const result = item.status === 'NOT_RUN' ? 'BLOCKED' : item.status;
    lines.push('| ' + (i + 1) + ' | ' + label + ' | **' + result + '** | ' + detail + ' |');
  }
  if (report.fixtureCleanupStatus !== undefined) lines.push('', 'Fixture cleanup: **' + report.fixtureCleanupStatus + '**. Mailbox cleanup: **' + (report.mailboxCleanupStatus || 'NOT_REPORTED') + '**.');
  lines.push('', 'Only sanitized check results are recorded. Credentials, mailbox contents, ticket access tokens, and full payment responses are excluded.', '');
  process.stdout.write(lines.join('\n') + '\n');
}
if (require.main === module) {
  if (process.argv[2] === 'init') init();
  else if (process.argv[2] === 'summary') summary();
  else throw new Error('Usage: node payment-qualification/report.cjs init|summary');
}
module.exports = { CHECKS, RESULT_PATH, blankReport, writeReport };
