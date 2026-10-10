# v2.0.36 — staging E2E self-provisioning and qualification hardening

## Changes

- Removed manual maintenance of the twelve per-test E2E fixture variables from the staging and enterprise browser workflows. A single coherent fixture set is created through authenticated API requests before stateful Playwright tests.
- Moved staff/manager creation to the current organizer-team API and rotates their initial passwords through the normal authenticated password-change endpoint before test use.
- Preserved privileged MFA: an existing administrator is never auto-enrolled, admin MFA must be supported by a known TOTP seed, and disposable event-manager MFA enrollment is only performed for the newly created disposable manager.
- Added hard target protection (`staging-events.neelastack.com` or an explicitly local loopback target), rejects production-marked env files, and allows checkout fixtures only when a valid Razorpay test-key set or Cashfree sandbox endpoint is configured.
- Restricted retries to safe requests; non-idempotent writes are not replayed after 5xx or transport failures. Recovery attempts discover fixtures using unique run identifiers after partial failures.
- Hardened cleanup to unassign staff/managers, deactivate generated users, cancel the generated event, and fail the qualification when teardown reports an error. A forcibly terminated runner can still interrupt teardown, so orphan review remains an operational safeguard.
- Added `npm run selftest`, an in-process mock API contract test for auth/MFA, environment-file parsing, sandbox selection, QR extraction, password rotation, partial-provisioning cleanup and cleanup failures.
- Set manual staging dispatch to run mutations by default while retaining read-only public-security-only scheduled runs.

## Security / operations notes

- Added a single least-privilege `E2E_STAGING_PROVISIONING_CONFIG` secret for staging marker, dedicated E2E admin/MFA credentials and sandbox gateway keys. The browser workflow never receives the full deployment `STAGING_ENV_FILE`, DB credentials, JWT-signing secrets or SMTP credentials; no per-test fixture ID/token secrets are needed.
- A valid dedicated staging admin identity is still required. With privileged MFA enabled, the admin's TOTP seed must be contained in the E2E-only config as `E2E_ADMIN_TOTP_SECRET`. Do not disable privileged MFA to make E2E pass.
- The enterprise load qualification remains a distinct workflow stage with its own `LOADTEST_*` requirements.
- Production application behavior was not intentionally changed. This release updates version metadata to 2.0.36 and hardens the E2E/qualification harness.
