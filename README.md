# Neelastack Events / Lakhdatar Events Platform

Version: `2.0.52` (candidate package; staging and production gates still required)

A multi-event ticketing and check-in platform with a Spring Boot backend, Angular SSR frontend, PostgreSQL, Redis, QR ticket credentials, staff mobile scanning, and payment-provider adapters for Razorpay and Cashfree. The repository also contains CI/CD, staging browser E2E, load-test, backup, rollback, and release-qualification workflows.

## Current release candidate

The v2.0.52 candidate targets a staging checkout failure observed in v2.0.51: Cashfree expects a 10-digit `customer_phone`, while an E2E request sent `+919999999999` and the adapter stripped punctuation but retained the `91` country code. The adapter now normalizes common Indian input formats and has mock HTTP regression tests. The candidate also restores the check-in latency SLO contract check, saves Playwright traces from staging runs, and stops release qualification from overriding the configured HTML reporter with line-only output. This is a code-level fix candidate, not proof that the external provider or deployed environment now passes. The workflow configuration in the supplied ZIP differs from the pasted run log on the missing-artifact policy; merge the targeted changes into the current Windows Git checkout and do not replace its `.git` folder or unreviewed newer changes.

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

See `docs/STAGING-RELEASE-FLOW.md`, `docs/PRODUCTION-RELEASE-CHECKLIST.md`, `docs/deployment.md`, `STAGING-QUALIFICATION-SETUP.md`, and `VALIDATION-2.0.52-CASHFREE-PHONE-AND-E2E-DIAGNOSTICS.md`.

## Important environment separation

- `STAGING_ENV_FILE` is a deployment secret and can contain DB/runtime signing secrets; never expose it to Playwright.
- `E2E_STAGING_PROVISIONING_CONFIG` must contain only the dedicated staging E2E admin identity, the admin TOTP seed where required, `APP_ENV=staging`, `DEFAULT_PAYMENT_PROVIDER`, and sandbox/test payment credentials. It must not contain DB passwords, JWT secrets, SMTP credentials, or `MFA_ENCRYPTION_KEY`.
- `E2E_ADMIN_TOTP_SECRET` is the Base32 TOTP seed, not the six-digit rolling code and not `MFA_ENCRYPTION_KEY`.
- Fixture IDs, bearer tokens, QR credentials and disposable event values are generated automatically by the E2E provisioner; do not store them as long-lived GitHub secrets.
- Keep production Cashfree on `https://api.cashfree.com/pg` and staging Cashfree on `https://sandbox.cashfree.com/pg`; the staging provisioner must not be configured with production payment keys.

## Release status

This archive is not certified for production. The latest supplied staging browser log had successful provisioning/self-tests but one failing checkout idempotency test (HTTP 502). A format mismatch has been fixed in this candidate and regression-tested locally when the backend test suite is run, but staging confirmation is still required. Never bypass the failing test or disable payment mutations to declare the release ready.
