# v2.0.51 — Cashfree Order Expiry Validation

Date: 2026-10-20
Scope: Local validation of release version 2.0.51 and the Cashfree order-expiry change.

## Backend

- Command: `mvn -B -ntp clean verify`
- Result: BUILD SUCCESS
- Test suites: 66
- Tests: 247
- Failures: 0
- Errors: 0
- Skipped: 14
- Artifact: `backend/target/lakhdatar-events-2.0.51.jar`
- The Cashfree HTTP contract tests passed, including the order-expiry request-field regression test.
- The release-contract test passed after updating the active release manifest.

## Frontend

- Environment: Node 24.21.0, npm 11.19.0
- Clean dependency installation: PASS (`npm ci`; 530 packages added)
- `npm run verify:dependencies`: PASS
- `npm run verify:baseline`: PASS
- `npm run test:unit`: PASS (12 tests across 3 files)
- `npm run build`: PASS (browser and server bundles generated)
- `npm run audit:runtime`: PASS (0 production/runtime vulnerabilities reported)
- `npm run audit:high`: PASS at the configured high-severity threshold.
- Residual finding: npm audit reported 3 moderate findings involving Vitest / @vitest/mocker in development/build tooling. These findings have not been remediated by this release.

## Version consistency

- Root VERSION, backend Maven version, frontend package and lockfile versions, and E2E package and lockfile versions are set to 2.0.51.
- Active sample deployment and application version defaults were updated to 2.0.51.
- Historical 2.0.50 release records are retained.

## Limitations and outstanding gates

- This record documents local build and test results only.
- Staging deployment, staging E2E, payment-provider verification against the staging environment, and release qualification are not certified by this record.
- This record is not production-readiness certification and does not authorize production deployment.
- Existing provider orders are not modified or reconciled by this change.
- Operational production evidence and exact certified-SHA gates remain required.
