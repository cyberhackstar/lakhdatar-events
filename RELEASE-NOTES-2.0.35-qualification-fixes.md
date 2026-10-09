# Qualification fixes for the supplied v2.0.35 source

This patch addresses two reproducible CI harness failures without changing application runtime behavior:

1. Enterprise frontend unit tests now invoke the repository's declared `test:unit` script (`ng test --watch=false`) rather than passing the unsupported Karma-style `--browsers=ChromeHeadless` flag to the configured Vitest-based Angular test builder.
2. Flyway qualification creates `lk_fresh` and `lk_upgrade` using separate `psql -c` calls. PostgreSQL rejects `CREATE DATABASE` statements combined inside a transaction block; the previous single multi-statement call stopped qualification before Flyway ran.
3. Qualification artifact uploads no longer silently ignore missing output for the database evidence. Browser/load/DAST artifact uploads emit warnings when their expected output directories are absent, making missing evidence visible without obscuring the primary job failure.
4. Removed a duplicate `E2E_STAFF_BEARER` environment declaration in the workflow.

## Verification performed in this packaging environment

- `bash -n infra/certification/flyway-qualification.sh`: passed.
- `node tools/verify-platform-baseline.mjs`: passed.
- `node tools/verify-frontend-lock.mjs`: passed.
- Backend Maven tests were not run because Maven is not installed in this packaging environment.
- Frontend tests were not run: the available Node is v22.16.0 while the project requires >=22.19.0 <23 or >=24 <25; dependency installation timed out and left no usable Angular CLI.
- Flyway qualification was not run because Docker is not available/was not invoked in this environment.

## Release status

These changes repair known qualification workflow defects. They do **not** certify the application as production-ready or prove capacity for thousands of concurrent users. Before production, run the full backend/frontend suites with supported toolchains, the fresh-and-upgrade Flyway qualification, staging E2E with valid staging-only fixtures, payment/webhook reconciliation tests, and the staging load/security gates. Review the existing payment-provider/webhook/recovery and 1,000-VU single-read-failure findings before release.
