# Neelastack Events / Lakhdatar Events Platform

Version: `2.0.56` (hotfix candidate; all CI, staging qualification and readiness gates must pass for its exact SHA)

A multi-event ticketing and check-in platform with a Spring Boot backend, Angular SSR frontend, PostgreSQL, Redis, QR ticket credentials, staff mobile scanning, and payment-provider adapters for Razorpay and Cashfree. The repository also contains CI/CD, staging browser E2E, load-test, backup, rollback, and release-qualification workflows.

## Current release candidate

**v2.0.56 is not yet qualified.** It retains the v2.0.55 stale-payment lifecycle fixes and addresses a load-qualification regression signal: the historical closed-order reconciliation lane previously ran in every fast sweep, and the candidate also changed payment retry ordering from oldest-attempt-first to newest-attempt-first. The historical reconciliation lane now runs separately at a lower cadence, both payment queries prefer the least-recently-attempted eligible payments, stale-open-order repair is isolated from the 15-second reservation-expiry loop (60-second cadence, 100-order batch), and checkout load failures log only a bounded sanitized API error code plus duration.

The v2.0.55 enterprise qualification failed in the checkout load scenario: 38 of 40 requests met the response contract, 2 returned server errors, and p95 latency was 8.33 seconds. The latency is consistent with the configured 8-second Cashfree HTTP read-timeout window, but the k6 log did not retain a safe API error code for the failing requests. The provider-side cause therefore remains unconfirmed. v2.0.56 adds this sanitized diagnostic without relaxing any thresholds; it must be requalified against Cashfree sandbox before release promotion.

This candidate still includes the bounded stale-order repair worker, provider-confirmed terminal/no-capture handling, the v2.0.57 operational-readiness kit, and read-only evidence-inventory helper. Provider timeouts and unknown payment states remain unresolved rather than being treated as failed payments.

**Production decision: HOLD.** The candidate has not passed Maven/JUnit, GitHub CI, staging deployment, mutation-enabled Cashfree sandbox E2E, load qualification, DAST, or the separately protected single-node readiness gate. Review `CHANGES-2.0.56-CHECKOUT-LOAD-RECONCILIATION.md`, `VALIDATION-2.0.56-RELEASE-QUALIFICATION.md`, and `docs/GITHUB-ENVIRONMENT-VALUES-v2.0.56.md` before qualification.

## Layout

- `backend/` — Spring Boot / Java 21 REST API, payments, reservations, reconciliation, QR/check-in, auth/MFA, Flyway migrations.
- `frontend/` — Angular SSR web app.
- `e2e/` — Playwright browser qualification and isolated auto-provision/teardown logic.
- `infra/docker-compose.staging.yml` — isolated staging stack.
- `infra/docker-compose.prod.yml` — single-node production stack.
- `infra/deploy/` — immutable-SHA deploy, staging deploy, and rollback scripts.
- `infra/backup/` — database backup and scheduled retention.
- `infra/loadtest/` — k6 scenarios and thresholds.
- `.github/workflows/` — CI, staging, E2E, load test, release qualification, single-node readiness, enterprise HA/certification and rollback workflows.
- `docs/` — architecture, deployment, release-flow and incident runbooks.

## Release workflow

1. Make source changes in your Windows local repository / VS Code. Do not patch production source files directly on the VM.
2. Run backend verification (`mvn -B -ntp clean verify` in `backend`), frontend checks (`npm ci`, dependency/baseline checks, unit tests/build/audits in `frontend`), E2E self-tests, and CI contract scripts.
3. Commit and push the reviewed change to `main`. GitHub Actions CI must pass for the exact SHA.
4. Deploy that exact SHA to isolated staging using `Staging Deploy`. Confirm the staging image/version is the same SHA.
5. Run `enterprise-e2e-staging` with mutations enabled using disposable fixtures, then run the staging payment verification and applicable load tests. Ensure teardown completed.
6. Run `Enterprise Release Qualification` and the protected single-node production-readiness workflow with all required operational evidence bound to the same SHA.
7. Promote only the exact certified SHA through the production workflow, with environment approval. Backups, readiness, smoke checks, immutable images, signing/provenance verification and rollback constraints still apply.

See `docs/STAGING-RELEASE-FLOW.md`, `docs/PRODUCTION-RELEASE-CHECKLIST.md`, `docs/deployment.md`, `STAGING-QUALIFICATION-SETUP.md`, and `VALIDATION-2.0.53-CASHFREE-ORDER-EXPIRY-COMPATIBILITY.md`.

## Important environment separation

- `STAGING_ENV_FILE` is a deployment secret and can contain DB/runtime signing secrets; never expose it to Playwright.
- `E2E_STAGING_PROVISIONING_CONFIG` must contain only the dedicated staging E2E admin identity, the admin TOTP seed where required, `APP_ENV=staging`, `DEFAULT_PAYMENT_PROVIDER`, and sandbox/test payment credentials. It must not contain DB passwords, JWT secrets, SMTP credentials, or `MFA_ENCRYPTION_KEY`.
- `E2E_ADMIN_TOTP_SECRET` is the Base32 TOTP seed, not the six-digit rolling code and not `MFA_ENCRYPTION_KEY`.
- Fixture IDs, bearer tokens, QR credentials and disposable event values are generated automatically by the E2E provisioner; do not store them as long-lived GitHub secrets.
- Keep production Cashfree on `https://api.cashfree.com/pg` and staging Cashfree on `https://sandbox.cashfree.com/pg`; the staging provisioner must not be configured with production payment keys.

## Release status

This archive is not certified for production. The previous staging browser run had successful provisioning/self-tests but checkout failed with HTTP 502 after Cashfree returned HTTP 400 `order_expiry_time_invalid`. The candidate updates the expiry policy and serialization, but real sandbox checkout/idempotency, payment confirmation, ticket issuance, scanning, and full release qualification must pass on the exact SHA. Never bypass a failing test or disable payment mutations to declare the release ready.
