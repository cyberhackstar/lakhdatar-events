# v2.0.52 — Cashfree phone normalization and E2E diagnostics

## Changes

- Adapt Indian phone inputs such as `+91 99999-99999` to Cashfree Create Order's expected ten-digit `customer_phone` format.
- Reject malformed phone values locally with `INVALID_CUSTOMER_PHONE` (HTTP 400) instead of passing them to Cashfree and obscuring the provider validation error as HTTP 502.
- Add local HTTP-contract tests for normalized and malformed customer phone values.
- Restore the check-in latency SLO contract check in the CI workflow.
- Upload the Playwright HTML report and `e2e/test-results` traces/logs from staging E2E runs.
- Remove the release-qualification CLI override that forced line-only reporting and prevented HTML report generation.
- Add a CI source-contract test to protect the browser-artifact collection paths.
- Bump active source/package/deployment version metadata to 2.0.52.

## Scope and safety

This is a targeted candidate based on the v2.0.51 source ZIP and the staging E2E log. The phone format mismatch is a strong, source-supported candidate for the observed Cashfree checkout 502, but it has not yet been confirmed against the live staging provider response. No payment secrets, deployed database state, or staging credentials are included or changed.

The code is not authorized for production until CI, staging deploy, full disposable-fixture E2E (including checkout idempotency and check-in), staging payment-provider verification, and the protected release/operational readiness gates pass for the same commit SHA.
