'use strict';
const API_BASE = 'https://api.mailslurp.com';
function required(name) {
  const value = String(process.env[name] || '').trim();
  if (!value) throw new Error(name + ' is required; refusing to run payment qualification without a disposable inbox.');
  return value;
}
async function api(path, options = {}) {
  const key = required('MAILSLURP_API_KEY');
  const method = options.method || 'GET';
  const body = options.body;
  const response = await fetch(API_BASE + path, {
    method,
    headers: Object.assign({ 'x-api-key': key, accept: 'application/json' }, body ? { 'content-type': 'application/json' } : {}),
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) throw new Error('Disposable inbox API ' + method + ' failed with HTTP ' + response.status + '. Check protected staging MAILSLURP_API_KEY and account limits.');
  if (response.status === 204) return {};
  return response.json();
}
async function createInbox() {
  const result = await api('/inboxes/withOptions', {
    method: 'POST',
    body: { name: 'neelastack-staging-payment-' + (process.env.GITHUB_RUN_ID || Date.now()), tags: ['staging', 'automated-payment-qualification'], expiresIn: 1800000 },
  });
  if (!result.id || !result.emailAddress || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(result.emailAddress)) throw new Error('Disposable inbox service did not return an inbox ID and email address.');
  for (const value of [result.id, result.emailAddress]) if (process.env.GITHUB_ACTIONS === 'true') process.stdout.write('::add-mask::' + value + '\n');
  if (!process.env.GITHUB_ENV) throw new Error('GITHUB_ENV is unavailable; this helper must run in GitHub Actions.');
  require('node:fs').appendFileSync(process.env.GITHUB_ENV, 'MAILSLURP_INBOX_ID=' + result.id + '\nE2E_CHECKOUT_EMAIL=' + result.emailAddress + '\n', { mode: 0o600 });
  process.stdout.write('Disposable test inbox created; address and inbox ID are masked.\n');
}
async function deleteInbox() {
  const id = String(process.env.MAILSLURP_INBOX_ID || '').trim();
  if (!id) { process.stdout.write('No disposable inbox was created; cleanup not required.\n'); return; }
  if (!/^[0-9a-f-]{20,}$/i.test(id)) throw new Error('Disposable inbox identifier has invalid format; refusing deletion.');
  await api('/inboxes/' + encodeURIComponent(id), { method: 'DELETE' });
  process.stdout.write('Disposable test inbox deleted.\n');
}
async function main() {
  if (process.argv[2] === 'create') await createInbox();
  else if (process.argv[2] === 'delete') await deleteInbox();
  else throw new Error('Usage: node payment-qualification/mailbox.cjs create|delete');
}
if (require.main === module) main().catch((error) => { process.stderr.write(error.message + '\n'); process.exitCode = 1; });
module.exports = { api };
