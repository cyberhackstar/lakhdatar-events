# Project handover and deployment plan — Neelastack Events

Date: 20 October 2026
Candidate: v2.0.53 — Cashfree expiry compatibility
Base source: uploaded v2.0.51 ZIP (`lakhdatar-events-main`), compared with v2.0.50 ZIP

## 1. Product and architecture

Lakhdatar Events / Neelastack Events is a ticket sales and event-operations platform intended to let organizers publish events, sell tickets online, accept payment-provider sessions, issue tickets with QR credentials, and validate those credentials from staff mobile browsers without dedicated scanning hardware.

- Backend: Java 21 / Spring Boot REST API, PostgreSQL with Flyway migrations, Redis-backed/rate-limited runtime controls, JWT/authentication and MFA, organizer/admin/staff access boundaries, reservations, orders, payment reconciliation/refunds, tickets and QR check-in.
- Frontend: Angular with SSR; public catalogue/event pages, checkout/payment return, customer ticket access, admin/organizer and staff-scanner flows.
- Payment providers: Razorpay and Cashfree adapters; staging must use sandbox/test credentials and a sandbox endpoint. Webhooks and reconciliation remain required parts of production payment operations.
- Infrastructure: Docker Compose for isolated staging and the single-node production topology, edge reverse proxy, Cloudflare/Tunnel deployment configuration, readiness/health checks, immutable SHA image builds, signing/provenance, database backup and rollback procedures.
- Browser verification: Playwright runs public and customer-ticket checks across Chromium, Android Chrome and iOS Safari; the mutable checkout/check-in suite runs once on Chromium. Fixtures are auto-provisioned and cleaned up by the staging provisioner.
- Load/security/release gates: k6 scenarios, source-contract tests, DAST/security/dependency scans, release qualification and separate operational certification gates. A release qualification result does not itself certify production readiness.

## 2. ZIP comparison

The supplied v2.0.50 archive has 740 files after extraction; the updated v2.0.51 archive has 741. The updated archive adds Cashfree order-expiry alignment and associated HTTP tests, plus a portable evidence-ID validator change. It also removes `infra/loadtest/test-checkin-thresholds.sh` and the matching CI invocation present in v2.0.50. Because the existing k6 scenario and threshold manifest still carry the check-in p95 <500 ms, p99 <1000 ms, business-contract and server-error gates, the candidate restores that CI regression check.

## 3. Latest staging log diagnosis

The attached GitHub Actions browser run reported: staging provisioning succeeded; the E2E provisioning self-tests passed; 19 browser tests passed; six were skipped and one did not run; checkout idempotency failed on the **first** `POST /api/v1/public/checkout` because expected HTTP 200 but got HTTP 502. The second idempotency request therefore was not reached. The workflow tried to upload only `e2e/playwright-report/`, while the failure log referenced a trace under `e2e/test-results/`, and no artifact was uploaded.

The test submits `customerPhone: '+919999999999'`. The Cashfree adapter previously removed punctuation and sent `919999999999`. Cashfree's Create Order API examples/documented field contract use a 10-digit customer phone. This is a strongly supported candidate cause for a provider 4xx translated by the adapter to HTTP 502; the exact provider response was not included in the pasted workflow log, so the cause remains unconfirmed until staging is rerun and/or sanitized provider logs confirm it.

## 4. Changes in candidate v2.0.52

1. Normalize an Indian number such as `+91 99999-99999` or `09123456789` to ten digits at the Cashfree adapter boundary.
2. Reject malformed/missing Cashfree phone values before contacting the provider, using `INVALID_CUSTOMER_PHONE` with HTTP 400.
3. Add mocked HTTP-contract regression tests for country-code normalization and malformed input rejection.
4. Restore the CI check-in latency SLO contract test that disappeared from the updated ZIP.
5. Upload both Playwright HTML report output and `e2e/test-results/` so traces/error context are retained when staging E2E fails. The qualification workflow also no longer overrides the configured HTML reporter with line-only reporting. A CI contract test protects these paths.
6. Align active project version metadata to 2.0.52 and replace the historically outdated README with an accurate project/release index.

## 5. Status and release decision

**Current decision: staging candidate only; do not promote to production yet.** The patch is a source-supported fix candidate, but this environment cannot verify Cashfree credentials or the deployed staging service. The v2.0.51 validation document reports local backend/frontend checks, not the later staging E2E run, and itself lists staging and production gates as pending.

Before production:

- [ ] CI passes for the exact reviewed commit SHA, including restored check-in SLO test, backend, frontend, security scans, image build/signing and packaging contracts.
- [ ] Staging deploy runs that exact SHA and serving version is confirmed.
- [ ] `mvn -B -ntp clean verify` passes on the patched backend and includes the new Cashfree phone tests.
- [ ] Frontend lock/baseline/unit/build/audit checks pass.
- [ ] `npm ci` and `npm run selftest` pass in `e2e`.
- [ ] Full staging browser E2E with disposable mutations passes; specifically, checkout idempotency creates one provider order and retry returns the same logical order; check-in accepts the QR once and reports the duplicate as already used.
- [ ] Newly uploaded report/trace artifact exists for the run. If checkout still fails, inspect sanitized backend logs for `payment.provider.http.failed` (method, path, status, safe provider error code; never raw body/secrets/customer data).
- [ ] Confirm Cashfree sandbox order creation, payment-session launch, webhook signature path, payment verification/reconciliation, reservation expiry, ticket issuance and cleanup in staging.
- [ ] Run the release-qualification workflow, applicable load/security tests, and the protected single-node operational-readiness evidence workflow for that same SHA.
- [ ] Verify off-host encrypted backups and a restore drill, alerting, finance reconciliation, privacy, MFA and rollback procedure before production promotion.

## 6. Operator workflow and key handling

All application code changes should be made in the local Windows repository using VS Code, reviewed, committed and pushed; GitHub Actions owns the build/deploy. Do not patch application source directly on the production VM. Use the VM only for approved operational checks, logs, backups and recovery work.

Use the protected `staging` environment secret `E2E_STAGING_PROVISIONING_CONFIG` for the minimum disposable-test admin login/TOTP seed and sandbox payment values. Use the deployment-only `STAGING_ENV_FILE` for runtime configuration. Do not combine the two. The TOTP seed is the Base32 secret, not a time-based six-digit code; it is also not `MFA_ENCRYPTION_KEY`. Keep all real values in GitHub secret storage or protected deployment secret files, never in this ZIP or committed files.

For failed checkout diagnosis on the staging VM, run `docker compose ps --services` in the staging release directory to identify the backend service, then inspect only the bounded, sanitized recent log lines relevant to `payment.provider.http.failed`, `PAYMENT_PROVIDER_ERROR`, `PAYMENT_PROVIDER_UNAVAILABLE` and `order.checkout`. Do not print `.env`, response bodies, credentials, tokens or full customer records into logs or chat.

## 7. Useful paths

- `docs/architecture.md` — architecture overview
- `docs/deployment.md` and `docs/STAGING-RELEASE-FLOW.md` — release and deployment flow
- `docs/PRODUCTION-RELEASE-CHECKLIST.md` — operational release checklist
- `STAGING-QUALIFICATION-SETUP.md` — staging E2E secret and fixture requirements
- `docs/INCIDENT-RUNBOOKS.md` — operational incident procedures
- `infra/backup/` — backup and retention tooling
- `infra/deploy/` — staging/production deployment and rollback scripts
- `VALIDATION-2.0.52-CASHFREE-PHONE-AND-E2E-DIAGNOSTICS.md` — checks actually run for this archive and remaining gates
