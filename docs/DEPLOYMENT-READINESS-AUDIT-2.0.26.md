# Neelastack Events v2.0.26 — Deployment Readiness Audit

## Scope
Reviewed the supplied `neelastack-events-v2_0_26-deploy-fixed.zip` for CI/CD, staging deployment, release qualification, production deployment safety, environment isolation, and shell/workflow failure modes.

## Corrective changes applied in this package

1. Staging SSH target handling now treats `STAGING_DEPLOY_HOST` as the Oracle VM IP/hostname and connects as `ubuntu`. An already-prefixed `ubuntu@host` value remains backward-compatible.
2. Staging deployment now runs a remote Docker/Compose/filesystem preflight before uploading deployment files.
3. Staging `.env` generation removes any old `IMAGE_NAMESPACE` / `IMAGE_TAG` lines and writes the exact release SHA plus the repository GHCR owner, preventing duplicate dotenv keys.
4. Staging deployment validates that `IMAGE_NAMESPACE` is a real GHCR owner namespace and rejects the example placeholder.
5. Certification/load/HA workflows invoke repository shell scripts through `bash` where appropriate, so CI no longer depends on executable-bit preservation when files are transferred from ZIP/Windows workflows.
6. Enterprise release qualification explicitly configures Node from `.nvmrc` before running Node-based repository validators.
7. Enterprise release qualification and the standalone staging load-test workflow are restricted to the canonical `https://staging-events.neelastack.com` origin.
8. Staging setup documentation now matches the SSH secret semantics.
9. Staging cleanup now uses the normalized SSH target, so a bare VM IP works consistently for deploy, GHCR logout and cleanup.
10. Enterprise browser E2E, load qualification and DAST are pinned to the canonical staging origin instead of accepting arbitrary HTTPS targets.
11. All packaged shell scripts are executable, while workflow invocations use `bash` where file-mode preservation should not be trusted.

## Static validation performed

- All repository shell scripts: `bash -n` PASS (23 scripts).
- All repository shell scripts are packaged with executable mode (`0755`).
- All eight GitHub Actions workflow YAML files: parsed successfully.
- `node tools/verify-platform-baseline.mjs`: PASS.
- `node tools/verify-frontend-lock.mjs`: PASS.
- Flyway migration sequence: V1..V41 present; certification expectation is 41.
- No merge-conflict markers or oversized accidental artifacts found.
- Compose structure and required variable references were reviewed for production and staging.

## Execution boundary

This packaging environment does not have Maven, Docker or an internet-capable npm registry cache sufficient to execute a clean backend build, Docker image build, or full browser/load/DAST run. Therefore this audit does **not** claim an independent end-to-end deployment was executed here. The exact operational gates remain GitHub CI, staging deployment, staging E2E/load/DAST, and the protected release qualification workflow.

## Required external configuration before staging can pass

- GitHub Environment `staging` secrets: `STAGING_DEPLOY_HOST`, `STAGING_DEPLOY_SSH_KEY`, `STAGING_DEPLOY_KNOWN_HOSTS`, `STAGING_ENV_FILE`.
- Cloudflare tunnel route: `staging-events.neelastack.com -> http://localhost:4003`.
- Staging `STAGING_ENV_FILE` must contain unique non-placeholder application secrets and at least one sandbox payment provider credential set.
- Dedicated staging E2E/load-test records and credentials must be provisioned before stateful qualification.

## Promotion rule

Do not run the production workflow for a release until the exact same 40-character SHA has passed CI, staging functional/E2E checks, staging load qualification, DAST, and the protected enterprise release qualification gate.
## v2.0.26 test-environment hardening

- Maven now pins Hibernate ORM to `7.2.25.Final`, a Spring Boot 4-compatible patch release, to avoid the observed ORM bootstrap NPE on the earlier managed patch level.
- Maven disables incremental compiler reuse so a clean CI/local build cannot accidentally reuse stale generated classes.
- The staging E2E workflow explicitly refuses the production origin before asserting the canonical staging URL.
