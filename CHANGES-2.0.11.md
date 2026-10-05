# v2.0.11 — Enterprise qualification and runtime hardening

## Implemented
- Fixed Razorpay webhook persistence compile regression.
- Split provider webhook recovery locks.
- Distributed-lock protected password-reset, MFA, and durable mail maintenance sweeps.
- Production now fails closed unless privileged MFA is explicitly enabled.
- Added isolated Playwright enterprise E2E suite for public security, ticket/PDF access, admin/staff authorization, checkout idempotency, and QR single-use behavior.
- Added staging-only E2E workflow with fail-closed production-origin protection.
- Added immutable-digest DAST runner.
- Added HA failover, disaster-recovery, payment-chaos, and SLO certification runbooks.
- Made frontend release-version verification derive from root VERSION instead of hardcoded release numbers.
- Updated active release metadata to v2.0.11.

## Remaining external qualification
- Full Maven/Angular/Playwright execution must run in CI/staging.
- HA, PITR, DAST, and payment-chaos reports require actual environment evidence and operator sign-off.

## Final enterprise qualification layer
- Added multi-browser Playwright qualification (desktop Chromium, Android Chrome, iOS Safari) with stateful mutations isolated to one staging project.
- Added exact-SHA production promotion gate via `ENTERPRISE_CERTIFIED_SHA`.
- Added staging DAST runner requiring an immutable scanner-image digest.
- Strengthened production smoke tests with security-header validation and invalid-ticket-token rejection.
- Added operational SLO, incident, financial-reconciliation, privacy/data-governance, HA failover, DR and payment-chaos runbooks.
- Added critical monitoring for webhook dead letters and refunds requiring manual review.
