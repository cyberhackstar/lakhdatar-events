# Neelastack Events / Lakhdatar Events Platform

Version: `2.0.56` (hotfix candidate; all CI, staging qualification and readiness gates must pass for its exact SHA)

A multi-event ticketing and check-in platform with a Spring Boot backend, Angular SSR frontend, PostgreSQL, Redis, QR ticket credentials, staff mobile scanning, and payment-provider adapters for Razorpay and Cashfree. The repository also contains CI/CD, staging browser E2E, load-test, backup, rollback, and release-qualification workflows.

## Current release candidate

**v2.0.56 application release; qualification harness correction in progress.** The v2.0.56 application includes the v2.0.55 stale-payment lifecycle hardening, oldest-attempt-first reconciliation, lower-rate historical reconciliation, and bounded stale-open-order repair. The latest exact-SHA enterprise run passed backend, frontend, database, browser E2E, staging identity and DAST. Its load job failed after one checkout request returned status `0` because the TCP connection to the Cloudflare edge was reset; the final checkout response had no HTTP 5xx, and the measured p95/p99 were 522.95/656.39 ms. The failure indicates a small-sample transport-flake issue in the qualification probe, not evidence of a new application 5xx regression.

The qualification-harness patch retries only one no-response (`status === 0`) request with the exact same idempotency key, never retries a received HTTP 4xx/5xx response, and keeps the final logical checkout thresholds strict. It also records the app release SHA separately from the immutable test-harness SHA so the already deployed app can be requalified without staging redeployment. This does not grant qualification automatically; the exact SHA must still pass every gate.

The v2.0.56 application retains the bounded stale-order repair worker, provider-confirmed terminal/no-capture handling, the v2.0.57 operational-readiness kit, and read-only evidence-inventory helper. Provider timeouts and unknown payment states remain unresolved rather than being treated as failed payments.

**Production decision: HOLD until requalification and single-node readiness pass.** No production deployment is authorized by a local ZIP or a partially successful workflow. See `docs/QUALIFICATION-HARNESS-TRANSPORT-RESET-FIX.md` and the v2.0.56 release-validation docs.

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
