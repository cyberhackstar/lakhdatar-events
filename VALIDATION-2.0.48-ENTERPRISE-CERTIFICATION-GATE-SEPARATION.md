# v2.0.48 validation record

## Source-derived failure

The uploaded GitHub Actions run checked out v2.0.47 SHA `38a39144e59ddc906a3b60698b99a3568bd62319` and executed the `enterprise-evidence` job only after its declared dependencies (`backend`, `frontend`, `database`, `browser-e2e`, `load`, and `dast`) completed. The evidence step failed immediately because `HA_FAILOVER_EVIDENCE_ID` was missing. The environment dump also showed topology, RPO/RTO, all other evidence IDs, and all PASS status values empty.

## Offline checks

- `bash infra/certification/test-verify-enterprise-evidence.sh` — passed: all missing fields reported together; invalid evidence produced no certification manifest; valid isolated fixtures produced a schema-checked manifest; multi-digit HA counts accepted; RPO over policy rejected.
- `bash infra/certification/test-certification-workflow-separation.sh` — passed: qualification can complete independently, production certification requires matching qualification artifact and protected operational evidence, and production deployment still requires `ENTERPRISE_CERTIFIED_SHA`.
- `bash infra/certification/test-verify-enterprise-evidence.sh` — PASS (aggregated missing-field diagnostics, no stale manifest on failure, positive fixture, multi-digit HA node count, and RPO threshold rejection).
- `bash infra/certification/test-certification-workflow-separation.sh` — PASS (qualification/certification separation and continued protected production SHA guard).
- Executed the real release-manifest generation and certification-workflow manifest-verification command bodies against an isolated temporary Git repository — PASS; SHA/run-ID mismatch was rejected.
- Inherited regression tests — PASS: scenario rate caps, checkout target/idempotency, reservation-expiry retry, JIT JWT refresh, token-refresh wiring, and single-owner HSTS.
- `node tools/verify-platform-baseline.mjs`, `node tools/verify-frontend-lock.mjs`, and `node tools/verify-security-policy.mjs` — PASS.
- All shell files under `infra/` passed `bash -n`; Node syntax checks passed for the load-test runner, policy verifier, staging provisioner and checkout k6 scripts.
- All 9 GitHub workflow YAML files parsed successfully; package/Maven/E2E versions align at 2.0.48.
- Full Maven verification, full Angular build/unit suite, live staging E2E/DAST/load, and live HA/PITR drills have not been executed as part of packaging this source ZIP; use the GitHub workflow for the corresponding end-to-end qualification.

The packaging environment has no Maven or Docker CLI, so full Maven verification and container/database integration were not run locally. No claim is made here that v2.0.48 has passed live staging, live DAST, browser E2E, k6 load, or production failover/PITR tests. These require the respective CI/staging/production targets.
