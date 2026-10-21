# Neelastack Events / Lakhdatar Events Platform

Version: `2.0.53` (candidate package; staging and production gates still required)

A multi-event ticketing and check-in platform with a Spring Boot backend, Angular SSR frontend, PostgreSQL, Redis, QR ticket credentials, staff mobile scanning, and payment-provider adapters for Razorpay and Cashfree. The repository also contains CI/CD, staging browser E2E, load-test, backup, rollback, and release-qualification workflows.

## Current release candidate

**Superseded by v2.0.54**, which corrects this: the 20-minute default was documented but never applied to the configuration files, and the effective hold is now floored at 16 minutes in code (see `CHANGES-2.0.54-CASHFREE-EXPIRY-ROOT-CAUSE.md`). The v2.0.53 candidate targeted the staging Cashfree response `order_expiry_time_invalid` observed after v2.0.52. The source had passed the reservation expiry to Cashfree, but the default local reservation hold was 12 minutes; the candidate raises the default hold to 20 minutes and serializes the provider expiry at whole-second UTC precision. A regression test exercises an `Instant` with fractional nanoseconds. This is a targeted compatibility candidate, not proof that Cashfree has accepted a real order. The actual deployed `RESERVATION_HOLD` in the protected `STAGING_ENV_FILE` can override defaults and must be `20m` (or be omitted to inherit the new Compose default) during staging qualification.

## Layout

- `backend/` — Spring Boot 3 / Java 21 REST API, payments, reservations, reconciliation, QR/check-in, auth/MFA, Flyway migrations.
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
