# Neelastack Events / Lakhdatar Events Platform

Version: `2.0.57` (**production-hardening patch candidate; not certified**)

A multi-event ticketing and check-in platform with a Spring Boot backend, Angular SSR frontend, PostgreSQL, Redis, QR ticket credentials, staff mobile scanning, and payment-provider adapters for Razorpay and Cashfree. The repository also contains CI/CD, staging browser E2E, load-test, backup, rollback, and release-qualification workflows.

## Current release candidate — v2.0.57

This package builds on the supplied v2.0.56 ZIP and hardens deployment and release controls. It updates production migration-failure handling, ensures only tracked files from the exact candidate commit are transferred to the VM, tightens trusted-main workflow/provenance checks, automatically validates the successful exact-SHA single-node readiness artifact, makes staging runtime configuration updates opt-in, makes SMTP and observability contracts consistent, validates the selected payment provider's credentials, passes application config through Compose, and adds regression tests for these contracts.

**Production decision: HOLD.** The previous recorded Enterprise Release Qualification (`39984461601`) failed its load gate after one checkout request returned no HTTP response during a TCP reset at the Cloudflare edge. This ZIP has not run a fresh full GitHub Actions cycle or real staging qualification. The launch legal/contact/refund config also still contains owner-confirmation TODOs, and the live provider, VM, backup restore, DNS/routing, and alerting settings are not accessible to this offline review. The new qualification gate blocks until the owner confirms the actual legal entity, complete address, monitored support/grievance contacts, grievance officer, and refund processing policy.

Do not infer production readiness from static checks or a local ZIP. Run full CI for the merged SHA, deploy the exact SHA to staging, pass sandbox checkout/payment/ticket/scanning E2E and release qualification, complete real single-node operational evidence, then use the protected production deployment approval.

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

See `docs/STAGING-RELEASE-FLOW.md`, `docs/PRODUCTION-RELEASE-CHECKLIST.md`, `docs/deployment.md`, `STAGING-QUALIFICATION-SETUP.md`, `docs/GITHUB-ENVIRONMENT-VALUES-v2.0.57.md`, and `VALIDATION-2.0.57-PRODUCTION-HARDENING.md`.

## Important environment separation

- `STAGING_ENV_FILE` is a deployment secret and can contain DB/runtime signing secrets; never expose it to Playwright.
- `E2E_STAGING_PROVISIONING_CONFIG` must contain only the dedicated staging E2E admin identity, the admin TOTP seed where required, `APP_ENV=staging`, `DEFAULT_PAYMENT_PROVIDER`, and sandbox/test payment credentials. It must not contain DB passwords, JWT secrets, SMTP credentials, or `MFA_ENCRYPTION_KEY`.
- `E2E_ADMIN_TOTP_SECRET` is the Base32 TOTP seed, not the six-digit rolling code and not `MFA_ENCRYPTION_KEY`.
- Fixture IDs, bearer tokens, QR credentials and disposable event values are generated automatically by the E2E provisioner; do not store them as long-lived GitHub secrets.
- Keep production Cashfree on `https://api.cashfree.com/pg` and staging Cashfree on `https://sandbox.cashfree.com/pg`; the staging provisioner must not be configured with production payment keys.

## Release status

This archive is not certified for production. The previous staging browser run had successful provisioning/self-tests but checkout failed with HTTP 502 after Cashfree returned HTTP 400 `order_expiry_time_invalid`. The candidate updates the expiry policy and serialization, but real sandbox checkout/idempotency, payment confirmation, ticket issuance, scanning, and full release qualification must pass on the exact SHA. Never bypass a failing test or disable payment mutations to declare the release ready.
