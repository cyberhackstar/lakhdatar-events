# Validation plan — v2.0.54

This is a checklist. None of the gates below are claimed as passed.

1. `cd backend && mvn -B -ntp clean verify` (includes the two new tests and the updated wire-format assertion).
2. `cd frontend && npm ci && npm run verify:baseline && npm run verify:dependencies && npm test && npm run build`.
3. `cd e2e && npm ci && npm run selftest`.
4. Remove or set `RESERVATION_HOLD=20m` in `STAGING_ENV_FILE`; deploy the exact release SHA to staging.
5. Run the mutation-enabled Playwright qualification on https://staging-events.neelastack.com. Expect the checkout idempotency test to pass on chromium, android-chrome and ios-safari, and the dependent QR/ticket tests to run instead of skip.
6. Confirm Cashfree App ID and secret are a matched sandbox pair (never live credentials on staging).
7. Single-node readiness evidence for the same SHA before any production promotion.
