# v2.0.52 — validation status

Date: 2026-10-20
Scope: Source-level fix candidate prepared from v2.0.51 after reviewing the supplied staging browser log.

## Changes applied

- Cashfree phone normalization + bad-input rejection.
- Mock HTTP contract regression tests for country-code-prefixed and malformed phone values.
- CI check-in latency SLO contract check restored.
- Playwright artifact collection expanded to include traces/error context, including the release qualification workflow’s previously overridden HTML reporter.
- Added a CI contract test for browser report/trace collection.
- Active release metadata updated to 2.0.52.

## Validation performed during packaging

- Both source ZIPs passed archive integrity checks.
- Source comparison and targeted source inspection were performed against the provided v2.0.50 and v2.0.51 archives and the supplied 20 October 2026 staging E2E log.
- Maven is not installed in the packaging environment, so backend compilation/JUnit execution has **not** been verified here.
- No staging Cashfree credentials or deployed-service access were available to run provider calls, staging E2E, payment verification or cleanup checks here.

## Required verification before deployment/promotion

- `mvn -B -ntp clean verify` in `backend`.
- Run `bash infra/loadtest/test-checkin-thresholds.sh`, `node infra/certification/test-browser-artifact-contract.mjs`, all other CI contract scripts and `node tools/verify-platform-baseline.mjs`.
- Frontend `npm ci`, `npm run verify:dependencies`, `npm run verify:baseline`, `npm run test:unit`, `npm run build`, `npm run audit:runtime`, `npm run audit:high`.
- E2E `npm ci` and `npm run selftest`; then full staging E2E with mutation fixtures.
- Confirm the 502 is cleared and the checkout idempotency test passes with the deployed candidate; validate payment-provider behavior and artifact upload.
- Pass same-SHA release qualification and protected operational single-node readiness before any production promotion.

Status: **not production-certified; no production deployment authorization implied.**
