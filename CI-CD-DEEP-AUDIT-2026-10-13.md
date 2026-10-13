# Lakhdatar Events — CI/CD Deep Audit (2026-10-13)

## Executive finding

**The uploaded Staging Deploy run failed because the workflow omitted a required root-level file from its VM deployment package.** The failed release SHA was `7c42d4ad92391da3817cd623895d6cf040cb7a1f`.

The log shows GHCR login succeeded and Cosign verified the backend, web, and edge release images. The workflow then installed the staging deployment files and invoked the remote script. The script exited at line 24 because `/home/ubuntu/apps/lakhdatar-events-staging/VERSION` did not exist. The deployment never reached its container pull/start steps or its external HTTPS smoke step. This is a file-packaging/order defect, not an image-signature error.

## Root cause and impact

`infra/deploy/deploy-staging.sh` derives `APP_VERSION` and OpenTelemetry `service.version` from `$ROOT/VERSION`. Before this fix, `.github/workflows/staging.yml` copied only the Compose file, deployment script, and smoke script. It did not copy `VERSION`.

The observed workflow also wrote the target environment file immediately before invoking the script. The failure occurred before application containers were changed, but the host `.env` could already contain the requested image tag while `.deploy-current` remained unchanged. An unchanged deployment marker is an important distinction: the intended release was not successfully recorded as deployed.

## Fixes in this source package

1. **Complete staging package:** copy `VERSION` from the exact checked-out release SHA and validate the version and all required remote files before changing the host environment file.
2. **Defensive deploy check:** the staging deploy script exits early with a direct message if `VERSION` is missing or empty; version input is normalized and validated.
3. **Safer environment transfer:** write to `.env.next` with restrictive permissions, then atomically rename it to `.env` so a broken SSH transfer cannot truncate the active file.
4. **Preflight parity:** reject `PRODUCTION_TOPOLOGY=enterprise-ha` during the workflow's staging environment validation, rather than letting the remote script discover it after the environment file is switched.
5. **Migration-safe error handling:** mark the staging rollback boundary before invoking backend startup, because a partially started backend may already have run Flyway. The workflow intentionally does not auto-switch old app images after this boundary.
6. **Gate image publishing on CodeQL:** the CI Docker publish job now requires backend tests, frontend tests, security, and CodeQL jobs to succeed.
7. **Verify what is actually deployed:** enterprise release qualification now checks the staging VM's `.deploy-current` marker against the exact requested 40-character SHA before browser, load, DAST, and final qualification gates can proceed. The certification/readiness manifests require this additional gate.
8. **Regression tests:** new staging deploy package/order assertions are wired into CI; existing private-repository compatibility and release qualification/certification separation tests were strengthened.

## Local validation results

The following passed against this source tree:

- `infra/certification/test-staging-deploy-package.sh`
- `infra/certification/test-certification-workflow-separation.sh`
- `infra/certification/test-private-repo-workflow-compat.sh`
- `infra/certification/test-verify-enterprise-evidence.sh`
- `infra/loadtest/test-checkout-target-guard.sh`
- `infra/loadtest/test-scenario-rate-limits.sh`
- `infra/loadtest/test-invariant-expiry-retry.sh`
- `infra/loadtest/test-loadtest-token-refresh-wiring.sh`
- `infra/smoke/test-hsts-ownership.sh`
- `node infra/loadtest/test-refresh-access-token.cjs`
- `node tools/verify-platform-baseline.mjs`
- `node tools/verify-security-policy.mjs`
- `node tools/verify-frontend-lock.mjs`
- `bash -n` on every shell script under `infra/`
- YAML parsing for all 10 GitHub Actions workflow files, plus an internal check that each declared `needs` dependency refers to an existing job
- Syntax-only `bash -n` accepted 111 GitHub Actions `run:` blocks after replacing GitHub expressions with inert placeholders

## Important validation limits

This was source and workflow-contract validation only. It was **not** a fresh GitHub-hosted CI run, an SSH deployment to your VM, or a live production qualification. Maven, Docker, `actionlint`, and `shellcheck` were unavailable in this runtime; the installed Node version is 22.16.0 while this repository specifies 24.21.0 in `.nvmrc`. Therefore a full Maven build, Angular install/build under the pinned Node, container build/pull, runtime smoke, Cashfree/Razorpay sandbox test, and real server backup/restore were not run here.

No source-level review can guarantee that future external services, secrets, runner images, network access, payment providers, or VM state will never fail. The added gates should fail earlier and make the deployment target explicit; hosted CI and exact-SHA staging deployment still need to pass before promotion.

## Recommended no-surprises release order

1. Apply the incremental patch only after `git apply --check` succeeds; review `git diff --check` and the diff before committing.
2. Push the commit and wait for **all** CI jobs, including CodeQL, to pass. Images must not publish when a required CI job fails.
3. Run Staging Deploy for that exact commit SHA. Confirm the job and public HTTPS smoke both pass.
4. Run Enterprise Release Qualification for the **same** SHA. Its staging identity step must match the remote `.deploy-current` marker; it will fail closed if staging still runs another SHA.
5. Do not treat this as production certification. Keep the protected production evidence, offsite backup/restore drill, PITR, and measured RPO/RTO requirements as separate promotion gates.
