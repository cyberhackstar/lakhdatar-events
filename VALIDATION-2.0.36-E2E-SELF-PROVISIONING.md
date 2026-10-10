# v2.0.36 validation record

## Completed in this workspace

- `node tools/verify-platform-baseline.mjs` — PASS.
- `node tools/verify-frontend-lock.mjs` — PASS.
- JavaScript syntax check (`node --check`) across E2E JavaScript files — PASS.
- YAML parse of workflow and Docker Compose YAML files — PASS (23 files).
- Provisioning control-flow self-test against an in-process loopback mock — PASS for cashfree-sandbox selection, env-file admin credentials, Razorpay test-key selection, MFA on/off, manager-only MFA enrollment, initial password rotation, QR-token flow, partial-provision cleanup, production-target/environment rejection, and cleanup failure gating.

The local registry could not resolve `registry.npmjs.org` in this workspace, so `npm ci` could not complete. For the mock control-flow run only, temporary local shims substituted QR PNG generation/decoding modules; those shims were removed before packaging. Thus the control-flow run is **not** evidence that the real pinned `qrcode`/`pngjs`/`jsqr` binaries have been integrated successfully. The release lockfile pins these dependencies, and CI must execute `npm ci && npm run selftest` with the real packages.

## Not executed here

- No live staging login, payment-sandbox request, browser run, or fixture teardown was attempted from this workspace; staging credentials and the staging environment are intentionally not present here.
- No production deployment, full backend/frontend build, or release-certification gate is claimed by this validation record.

## Required release gate

Before production promotion, deploy the immutable candidate to isolated staging and confirm the `Staging Deploy` workflow, both E2E workflow self-tests with real dependencies, the manual full staging browser run (`run_mutations=true`), cleanup behavior, the existing CI checks, and the enterprise load/DAST/database qualification stages. The browser E2E job no longer needs manually maintained per-test fixture IDs/tokens, but still needs an authenticated staging admin identity, the TOTP seed when privileged MFA is already enrolled, and a valid sandbox payment-provider configuration through the dedicated `E2E_STAGING_PROVISIONING_CONFIG` secret. The E2E workflow must not be given the full deployment `STAGING_ENV_FILE`.
