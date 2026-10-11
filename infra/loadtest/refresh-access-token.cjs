#!/usr/bin/env node
'use strict';

// Mint a fresh short-lived access token immediately before an authenticated load scenario.
// Only the pinned staging origin or a loopback development origin is allowed. Tokens are written to stdout for shell capture only;
// this process never logs credentials or tokens.
const { makeClient, login } = require('../../e2e/support/provision');
const STAGING_ORIGIN = 'https://staging-events.neelastack.com';

async function refreshAccessToken({ role, env = process.env, baseUrl = env.BASE_URL } = {}) {
  if (!baseUrl) throw new Error('BASE_URL is required');
  let parsed;
  try { parsed = new URL(baseUrl); } catch { throw new Error('BASE_URL must be an absolute URL'); }
  const localHost = ['127.0.0.1', 'localhost', '[::1]'].includes(parsed.hostname.toLowerCase());
  const approvedStaging = parsed.origin === STAGING_ORIGIN && parsed.protocol === 'https:';
  const approvedLoopback = localHost && ['http:', 'https:'].includes(parsed.protocol);
  if ((!approvedStaging && !approvedLoopback) || parsed.pathname !== '/' || parsed.search || parsed.hash || parsed.username || parsed.password) {
    throw new Error('Refusing to refresh credentials outside the approved staging or loopback origin');
  }

  let email = '';
  let password = '';
  let totpSecret = '';
  let label = '';
  if (role === 'staff') {
    email = String(env.STAFF_EMAIL || '').trim();
    password = String(env.STAFF_PASSWORD || '');
    label = 'Load-test scanner staff';
  } else if (role === 'admin') {
    email = String(env.ADMIN_REFRESH_EMAIL || '').trim();
    password = String(env.ADMIN_REFRESH_PASSWORD || '');
    totpSecret = String(env.ADMIN_REFRESH_TOTP_SECRET || '').trim();
    label = 'Staging admin';
  } else {
    throw new Error('REFRESH_TOKEN_ROLE must be staff or admin');
  }
  if (!email || !password) throw new Error(`Missing ${role} refresh credentials`);

  const client = makeClient(baseUrl.replace(/\/$/, ''), () => {});
  const result = await login(client, email, password, { totpSecret, label });
  if (!result || typeof result.token !== 'string' || !result.token.trim()) {
    throw new Error(`${label} login returned no access token`);
  }
  return result.token.trim();
}

if (require.main === module) {
  refreshAccessToken({ role: process.env.REFRESH_TOKEN_ROLE })
    .then((token) => process.stdout.write(`${token}\n`))
    .catch((error) => {
      // Errors from makeClient/login contain status/code only, never response bodies or credentials.
      process.stderr.write(`Access-token refresh failed: ${error && error.message ? error.message : 'unknown error'}\n`);
      process.exitCode = 1;
    });
}

module.exports = { refreshAccessToken };
