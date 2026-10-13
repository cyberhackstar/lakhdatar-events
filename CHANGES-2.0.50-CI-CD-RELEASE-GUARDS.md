# v2.0.50 — CI/CD deploy packaging and release identity hardening

## Confirmed failure from the 2026-10-13 staging run

The staging workflow copied the Compose file, deploy script, and staging smoke script to the VM but did not copy the repository-root `VERSION` file. `infra/deploy/deploy-staging.sh` requires that file to normalize `APP_VERSION` and OpenTelemetry metadata, so it exited with `No such file or directory` before its container pull/start sequence. The current release's GHCR authentication and Cosign signature checks had already succeeded.

## Changes

- Copy the `VERSION` file for the exact checked-out release SHA to the staging VM and validate it and all required deployment scripts before updating staging `.env`.
- Fail early with an actionable message if the staging deploy script is invoked without a non-empty `VERSION` file.
- Replace staging `.env` atomically through `.env.next` + `mv`; an interrupted SSH transfer should not truncate the active environment file.
- Add `infra/certification/test-staging-deploy-package.sh` to CI and the platform baseline checks to prevent this missing-file regression.
- Require CodeQL to pass before CI publishes immutable release images, in addition to backend, frontend, and security checks.
- Add a `staging-release-identity` gate to enterprise release qualification. It compares the requested SHA with the staging VM's `.deploy-current` marker before browser, load, DAST, and qualification-manifest gates can proceed. The downstream enterprise-certification and single-node-readiness workflows require this gate in the manifest.
- Move the staging automatic-rollback boundary before invoking backend startup. Even if `docker compose up` returns an error after partially starting the backend, the deploy trap will not automatically switch to old application images after Flyway may have changed the schema.

## Local validation performed

- The staging package/ordering/atomic environment regression test passes.
- Private-repository workflow compatibility regression test passes.
- Release qualification/certification separation test passes.
- Enterprise evidence, load-test safety/rate/invariant/token-refresh, and HSTS smoke regression tests pass.
- Platform baseline, security policy, and frontend dependency contract verifiers pass.
- All repository shell scripts under `infra/` pass `bash -n`.
- All 10 GitHub Actions workflow YAML files parse; every declared `needs` target exists. Syntax-only `bash -n` accepted 111 workflow `run:` blocks after replacing GitHub expressions with inert placeholders.

## Validation boundary / release instructions

This is a source-level CI/CD hardening package, not a claim that hosted GitHub Actions, a real SSH deployment, Docker image pulls, Maven/Angular builds, the payment sandbox, or production recovery have been run from this environment. This environment does not have Maven, Docker, `actionlint`, or `shellcheck`, and its Node version (22.16.0) does not match the project's `.nvmrc` (24.21.0). The next proof must be a fresh CI run, followed by a successful staging deployment of the exact commit SHA and enterprise release qualification for that same SHA.

Do not promote to production based on local static checks alone. Production promotion still requires the protected single-node/enterprise evidence gates, a verified offsite backup and restore, and actual PITR/RPO/RTO evidence. A GitHub Actions green check cannot substitute for those operational tests.
