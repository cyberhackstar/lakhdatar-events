# v2.0.46 — Just-in-time load-test token refresh

## Root cause

The enterprise load suite provisioned a scanner staff JWT before a multi-stage load run that lasts more than 25 minutes. The configured default JWT access-token lifetime is 15 minutes. `checkin.js` therefore sent an expired bearer token for all 240 requests, which explains the 100% HTTP failure rate with 0% server errors. The existing scanner test was placed after the longest tests, so it reliably outlived the initial token. The later admin operations scenario also reused the early admin JWT and was vulnerable to the same expiry.

## Changes

- Reuse the existing hardened login/MFA flow to obtain a fresh scanner bearer immediately before `checkin.js`.
- Obtain a fresh admin bearer immediately before `operations.js`, including the configured TOTP path when the admin requires MFA.
- Pin the refresh helper to `https://staging-events.neelastack.com`; it refuses alternate origins before sending credentials.
- Keep generated passwords and TOTP material in the host-side orchestration process; only the refreshed bearer is explicitly passed to the k6 container.
- Add bounded, sanitized scanner and hot-sale diagnostics that print only unexpected HTTP status and a whitelisted API code (never bodies or credentials).
- Add offline regression tests for staff login, admin MFA refresh, and origin refusal.
- Preserve v2.0.45 rate-limit status behavior, reservation invariant retries, HSTS ownership, CSP nonce handling, checkout contracts, and all existing thresholds.

## No security relaxation

JWT lifetime is not increased, authentication is not bypassed, rate limits are not removed, and load-test acceptance thresholds are not weakened.
