# v2.0.50 — Staging deployment VERSION packaging fix

## Root cause

The Staging Deploy workflow copied the Docker Compose file, staging deploy script, and smoke script to the VM, but `infra/deploy/deploy-staging.sh` also reads the repository-root `VERSION` file to set `APP_VERSION` and OpenTelemetry `service.version`. The missing file caused the workflow to exit before pulling or starting the release containers.

## Fixes

- Copy the `VERSION` file from the exact checked-out release SHA to the staging VM.
- Validate that `VERSION`, the staging Compose file, deployment script, and smoke script exist before replacing the VM `.env` file.
- Emit a direct actionable error if the deployment script is run without a non-empty `VERSION` file.
- Add a CI contract test and baseline assertion to prevent this packaging regression.

## Validation boundary

Source-level workflow contracts and repository tests must pass before merge. This patch does not claim that GitHub-hosted CI, a live staging deployment, payment integration, or production recovery has been executed from this environment.
