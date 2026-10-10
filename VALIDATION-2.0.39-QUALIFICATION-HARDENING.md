# Validation record — 2.0.39 qualification hardening

## CI failure triage and correction

The latest backend CI run compiled 140 main source files and 68 test source files, then ran 245 tests with exactly two failures:

1. `ProviderOrderRecoveryContractTest.freshProviderOrdersSkipReceiptLookupUntilAPreviousAttemptMayHaveReachedTheGateway` normalized source whitespace away, but its expected string accidentally retained a space (`"string prior..."`). The assertion now uses the whitespace-free expected token. This corrects the test, not production logic.
2. `ProductionHardeningV206ContractTest.edgeAndLockfileProductionContractsArePresent` failed because `VERSION`/POM/frontend were bumped to 2.0.39 while `RELEASE-MANIFEST.txt` still said `Release: 2.0.38`. The manifest now matches 2.0.39 and points to the matching change and validation records. The edge routes and lockfile vulnerability-reference assertions were already present and matched the prior known-good v2.0.38 reference archive.

These two corrections directly address the only failures reported by the supplied Maven log. A fresh `mvn -B -ntp clean verify` run is still required to confirm the entire suite is green after the corrections.

## Source-derived diagnoses

1. `infra/certification/flyway-qualification.sh`: `MAX(version)` was evaluated lexically because the
   Flyway `version` column is textual. Log evidence showed schema migration 41 had completed on both
   fresh and upgrade databases, then the query returned 9. Query now follows successful `installed_rank`.
2. `infra/security/dast-staging.sh`: ZAP could not write `/zap/wrk/zap.yaml` on the bind mount and
   attempted to resolve `/zap/wrk/zap/wrk/zap-baseline.html`. Output directory permissions and paths
   are corrected.
3. `infra/loadtest/enterprise-gate.sh`: logs showed required staging secrets empty. The full set of
   missing variable names is now reported safely and saved in the preflight artifact. A configured
   test fixture is still required; the load scenarios are not skipped or weakened.

## Executed checks

- `bash -n` on changed shell scripts.
- `node --check` on the E2E fixture and frontend dependency doctor.
- `node tools/verify-platform-baseline.mjs`.
- `node tools/verify-frontend-lock.mjs` and version/lock-root consistency assertions.
- `git diff --check` and ZIP integrity checks.

## Post-fix status and outstanding verification

- The supplied pre-fix GitHub Actions Maven run compiled 140 main Java source files and 68 test source files, then ran 245 tests: 2 failed, 243 passed, 0 skipped. Both failures were contract assertions, diagnosed above. The assertion and manifest fixes are included in this source, but a fresh full Maven run after these exact edits has not yet been available in this patching workspace (Maven installation timed out). Do not represent the post-fix suite as green until CI reruns successfully.
- Docker-based Flyway qualification, DAST against staging, and the k6 enterprise load gate need rerunning in GitHub Actions after these changes. The load gate also requires valid inputs in the protected GitHub `staging` environment.
- A real Cashfree sandbox checkout plus webhook/reconciliation proof is still required before production.
