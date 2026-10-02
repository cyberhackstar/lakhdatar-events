# Lakhdatar Events v1.9.8

## Fixed in v1.9.8

- Repaired the frontend npm lockfile so `npm ci` resolves `etag` to the real published `1.8.1` package instead of the invalid `1.9.0` tarball URL.
- Replaced the invalid unversioned Trivy Action reference with the immutable commit for Trivy Action v0.36.0.
- Consolidated four GitHub Actions workflows into two: `CI` and `Production`. The weekly security scan is now part of `CI`; deployment and manual rollback are unified in `Production`.
- Upgraded GitHub Actions to current Node 24-capable major/minor lines to remove the Node 20 runtime deprecation path.
- Added private GHCR authentication to Trivy image scans.
- Added a workflow-run guard so weekly scheduled CI security scans can never trigger a production deployment.
- Removed automatic application rollback after Flyway has run; schema rollback is not performed implicitly.
- Added explicit manual release SHA support for production deployment and protected-environment usage.
- Updated the frontend lock-refresh script and documentation to use Node 24 consistently.
- Updated deployment contract tests to the unified `Production` workflow.

## Validation

- JSON parsing and lockfile integrity checked.
- No invalid or unpinned Trivy Action references remain.
- No remaining `deploy.yml`, `rollback.yml`, or `security-scheduled.yml` workflow files.
- Third-party npm dependency versions were not intentionally upgraded; only the corrupted `etag` lock metadata was repaired.
- User-side `npm ci` remains the authoritative registry-backed verification.
