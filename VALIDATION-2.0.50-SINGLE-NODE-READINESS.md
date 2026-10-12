# v2.0.50 validation record

## Change validated

The single-VM production route now has a separate protected `Single-Node Production Readiness` workflow. It verifies the exact release-qualification manifest SHA and run ID before requiring real operational evidence for a single-node recovery drill, off-host backup, database restore/PITR, security assessment, payment recovery, alerting, finance reconciliation, privacy review and privileged MFA. Measured RPO/RTO must remain within configured maxima. Its output expressly sets `enterpriseHACertificationGranted=false`.

The ordinary `Production` workflow now only supports `PRODUCTION_TOPOLOGY=single-node` and `HA_NODE_COUNT=1`; it requires the exact reviewed `SINGLE_NODE_CERTIFIED_SHA`, an explicit single-node risk acknowledgement, signed-image/provenance verification and post-deploy smoke checks. Its rollback route also refuses a non-single-node topology. The separate enterprise certification and HA promotion workflows retain their existing HA requirements and `ENTERPRISE_CERTIFIED_SHA` boundary.

## Local checks performed

- `infra/certification/test-verify-single-node-readiness.sh` — PASS: aggregates missing evidence diagnostics, removes stale output after invalid rerun, accepts an isolated valid fixture, rejects the wrong topology, enforces RPO and verifies that the output does not grant HA certification.
- `infra/certification/test-certification-workflow-separation.sh` — PASS: qualification remains separate, HA certification stays protected, single-node readiness is separate, and single-node deploy requires its exact reviewed SHA.
- `infra/certification/test-verify-enterprise-evidence.sh` — PASS: existing enterprise HA validator retains fail-closed missing-evidence and measured RPO/RTO checks.
- Existing load-test checkout guard, scenario-rate, expiry-retry and just-in-time token-refresh wiring tests — PASS.
- Existing HSTS ownership smoke regression test — PASS.
- `tools/verify-platform-baseline.mjs`, `tools/verify-frontend-lock.mjs`, `tools/verify-security-policy.mjs` — PASS.
- All `infra/**/*.sh` passed `bash -n`; JavaScript/CJS/MJS under load-test, tools and E2E paths passed `node --check`.
- All 10 workflow YAML files parsed; `backend/pom.xml` and frontend/E2E package JSON/lockfiles parsed. Active version coordinates are aligned at 2.0.50.

The positive validator fixture uses local synthetic evidence IDs only to test validator behavior. It is not real production evidence and cannot certify an environment.

## Validation limitations

This environment does not have Maven or Docker available. Therefore `mvn -B -ntp clean verify`, Java contract tests, full Angular build/unit tests, Docker-backed integration tests and live staging E2E/DAST/load workflows were not executed as part of this packaging pass. Nor were the actual backup/restore and operational evidence exercises performed. The new workflow must pass on GitHub Actions against the final pushed SHA; an authorized reviewer must inspect the retained reports before setting `SINGLE_NODE_CERTIFIED_SHA` in the protected `production` environment. This ZIP alone does not certify production readiness.
