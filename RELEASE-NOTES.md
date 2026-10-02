# Lakhdatar Events v1.9.10

## Fixed in v1.9.10

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

## v1.9.9 — CI dependency security fix

- Added npm override forcing patched piscina 5.3.2 for Angular build tooling.
- Preserved Angular 20.3.x application runtime; no Angular-major upgrade.
- CI npm audit can now resolve the Oct 1 2026 piscina critical advisory without `npm audit fix --force`.
- Release workflows remain consolidated into CI + Production.

## v1.9.10 — hardened non-root edge

- Hardened the NGINX edge image to run as the built-in non-root `nginx` user (UID/GID 101).
- Moved NGINX to container port 8080 so the edge needs no `NET_BIND_SERVICE` capability.
- Preserved the external Oracle VM entry point at loopback `127.0.0.1:4002`.
- Added read-only-compatible runtime tmpfs ownership for NGINX cache and PID paths.
- Updated Compose, contract tests, deployment docs and release metadata for the internal 8080 edge port.
