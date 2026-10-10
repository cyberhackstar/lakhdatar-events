# Validation record — 2.0.39 qualification hardening

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

## Not executed here

- `mvn -B -ntp clean verify` (Maven/dependencies not available in the patching workspace).
- Docker-based Flyway qualification (Docker unavailable in the patching workspace).
- DAST against staging or the k6 enterprise load gate.
- A real Cashfree sandbox checkout plus webhook/reconciliation proof.
