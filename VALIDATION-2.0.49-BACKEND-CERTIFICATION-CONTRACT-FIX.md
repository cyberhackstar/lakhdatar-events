# v2.0.49 validation record

## Source-derived failure

The supplied v2.0.48 GitHub Actions Maven log reports `Tests run: 245, Failures: 1, Errors: 0, Skipped: 0`. The only failure is `EnterpriseReleaseV212ContractTest.enterpriseCertificationGateIsFailClosed` at line 41. That test expected the old combined certification workflow, even though v2.0.48 intentionally separated release qualification from production certification. The `HTTP Status: 401` message from the Flyway GitHub Packages metadata endpoint was a warning in this run, not the reported build-failure cause; Maven proceeded to compile and run the suite.

## Applied fix

The backend contract test now asserts that release qualification does not require the protected `enterprise-certification` environment. It separately asserts that `.github/workflows/enterprise-production-certification.yml` requires that environment, downloads the matching qualification artifact, and invokes the protected operational-evidence validator. Fail-closed assertions for HA evidence ID, HA status and production topology remain. No test has been disabled, skipped or loosened.

## Local validation on the packaged v2.0.49 source

- `bash infra/certification/test-verify-enterprise-evidence.sh` — PASS (aggregated missing-field diagnostics, no stale manifest on failure, valid positive fixture, multi-digit HA count and RPO threshold enforcement).
- `bash infra/certification/test-certification-workflow-separation.sh` — PASS (separate qualification/certification, matching qualification artifact, and unchanged protected production SHA requirement).
- `bash infra/loadtest/test-checkout-target-guard.sh` — PASS.
- `bash infra/loadtest/test-scenario-rate-limits.sh` — PASS (PDF/operations rate isolation and strict response contracts).
- `bash infra/loadtest/test-invariant-expiry-retry.sh` — PASS.
- `node infra/loadtest/test-refresh-access-token.cjs` — PASS.
- `bash infra/loadtest/test-loadtest-token-refresh-wiring.sh` — PASS.
- `bash infra/smoke/test-hsts-ownership.sh` — PASS.
- `node tools/verify-platform-baseline.mjs`, `node tools/verify-frontend-lock.mjs`, `node tools/verify-security-policy.mjs` — PASS.
- All shell files under `infra/` passed `bash -n`; load-test runner/policy/provisioner and checkout k6 JavaScript syntax checks passed.
- All 9 GitHub workflow YAML files parsed successfully.
- Active application/package versions align at 2.0.49; prior v2.0.48 ZIP inventory has 724 files, all retained, with 2 release documentation files added and 8 source/metadata files changed.
- Final ZIP integrity — PASS (726 files); no populated `.env`, `node_modules`, Maven `target`, or `.git` included.

## Validation limitations

This packaging environment has Java 21 but no Maven or Docker CLI, and outbound package access is unavailable. Therefore the updated JUnit test and complete `mvn -B -ntp clean verify` have not been executed here; neither have the full Angular build/unit tests, Docker-backed integration tests, or live staging E2E/DAST/load qualification. The provided v2.0.48 CI run had only the above single test failure; the updated backend suite and downstream workflows must pass in GitHub Actions after this ZIP is committed and pushed. No claim is made that v2.0.49 is already live-qualified or production-certified.
