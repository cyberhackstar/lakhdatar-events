# Lakhdatar Events v1.9.20 — production smoke-test and SSR proxy correction

- Fixes the false production deployment failure where the stack became fully healthy but the local smoke tests sent `Host: 127.0.0.1`; Angular SSR and backend request processing correctly rejected that synthetic host with HTTP 400.
- Local deployment smoke tests now send `Host: events.neelastack.com` and `X-Forwarded-Proto: https`, matching the Cloudflare -> loopback edge -> private service production contract.
- Smoke-test failures now identify the exact URL and HTTP status and print bounded response diagnostics.
- Explicitly enables Angular SSR trusted proxy headers for the headers NGINX controls and sets the production host allowlist to `events.neelastack.com`; CI's direct localhost SSR smoke test has its own explicit test-only host allowlist.
- Preserves the v1.9.19 NGINX PID/startup correction and fail-fast edge readiness diagnostics.
- Adds `docs/VM-PREDEPLOY-V1.9.20.md` with safe first-production VM preflight and targeted Lakhdatar-only cleanup guidance.

# Lakhdatar Events v1.9.19 — NGINX edge startup corrective release

- Permanently fixes the production edge CrashLoop caused by passing `pid /tmp/nginx.pid` through `nginx -g` while the official NGINX base configuration already defines a `pid` directive.
- Rewrites the base image PID path to `/tmp/nginx.pid` at image build time and starts NGINX with only `daemon off;`, eliminating duplicate PID configuration.
- Removes the redundant root-oriented `user nginx;` directive from the base NGINX configuration because the container already runs as the unprivileged `nginx` user.
- Adds a dedicated non-root startup validator that runs `nginx -t` before the master process starts and fails with a clear configuration error.
- Retains the correct Compose DNS topology (`backend:8080` and `web:3000`); standalone `docker run` tests are not the production network model.
- Hardens deployment edge readiness to distinguish restarting/exited containers, internal NGINX readiness failures, and host port `4002` binding failures; diagnostics now use direct Docker inspection/log commands instead of relying on Compose service arguments.
- Adds an edge startup grace period to the production healthcheck.
- Adds static regression checks so the duplicate-PID pattern cannot be reintroduced without CI detecting it.

# Lakhdatar Events 1.9.16 — CI security-audit correction

- The failed 1.9.16 CI run was caused by `npm audit --audit-level=high` evaluating development-only tooling dependencies.
- The reported high-severity findings were in the Karma/Angular test toolchain and npm/Sigstore registry tooling; these packages are not shipped in the production SSR image.
- The security job now installs and audits only production dependencies with `npm ci --omit=dev` and `npm audit --omit=dev --audit-level=high`.
- The frontend production image already uses `npm prune --omit=dev` during the build and installs runtime dependencies with `npm ci --omit=dev`, keeping the audited runtime dependency boundary aligned with the shipped image.

# Lakhdatar Events 1.9.16 — Stability Protection

- Blocks unplanned Angular-major Dependabot PRs while the supported frontend baseline remains Angular 20.x; patch/minor security updates remain enabled.
- Isolates local Docker Compose container names and PostgreSQL/Redis volumes from production so local development cannot collide with the production stack.

# Lakhdatar Events 1.9.16 — Cashfree Webhook Secret Model Correction

- Removed the non-existent application-side `CASHFREE_WEBHOOK_SECRET` configuration.
- Cashfree webhook HMAC verification now uses the Cashfree `CASHFREE_SECRET_KEY`, matching Cashfree's documented verification flow.
- Updated production guard, Compose environment, example environment, payment documentation, and regression tests accordingly.

# Release 1.9.16 — Redis production hardening corrective pass

- Corrects the Redis container security configuration that caused `setpriv: setresuid failed: Operation not permitted` during production recreation.
- Starts Redis directly as UID 999 / GID 1000, matching the `redis` account in the Redis 7 Alpine image, so the image entrypoint does not require a root-to-user privilege transition.
- Retains `no-new-privileges:true` and `cap_drop: ALL`; no additional Linux capabilities are granted to Redis.
- Keeps the existing named `lakhdatar_redis_data` volume and does not require destructive data reset or migration.
- Uses `REDISCLI_AUTH` in the healthcheck so the health probe authenticates without placing the password in the `redis-cli` command arguments.
- Adds Redis process/resource limits consistent with the other production services.

# Release 1.9.14 — supply-chain verification corrective pass

- Keeps the v1.9.13 Alpine/OpenSSL security hotfix and all prior CI/CD hardening.
- Replaces `cosign verify-attestation --type slsaprovenance` for BuildKit provenance with GitHub Artifact Attestations.
- Each image is attested from the exact `docker/build-push-action` digest and published to GHCR.
- CI verifies each immutable image with `gh attestation verify` before running `cosign sign`.
- Adds `artifact-metadata: write`, required for registry-backed GitHub Artifact Attestations.
- Strengthens the baseline validator so the legacy incompatible provenance-verification pattern cannot be reintroduced.

# Release 1.9.13 — Alpine security hotfix

- Keeps the corrected v1.9.12 release metadata and CI/CD hardening intact.
- Adds `apk upgrade --no-cache` to both frontend image stages so the final Node runtime receives current Alpine security updates.
- Adds the same Alpine security update step to the NGINX edge image.
- Strengthens `tools/verify-platform-baseline.mjs` to require these image hardening steps.
- The latest CI log shows the Java backend image at 0 vulnerabilities; the remaining blocker was the frontend image carrying Alpine OpenSSL 3.5.6-r0, which Trivy flagged as HIGH until updated.

# Release 1.9.12 — corrected enterprise production baseline

- Rebuilt from the verified v1.9.11 baseline rather than carrying forward the broken v1.9.12 metadata changes.
- Synchronized `VERSION`, frontend `package.json`, frontend `package-lock.json`, and Maven project version to `1.9.12`.
- Restored and explicitly validated the Testcontainers BOM (`1.21.4`) so `testcontainers:postgresql` and `junit-jupiter` resolve reliably in Maven and CodeQL.
- Retained security-patched Jackson 2.21.7 / 3.1.7, HttpComponents Core5 5.4.3 and Tomcat 11.0.26 overrides.
- Removed the GitHub Dependency Review job because the repository currently reports that Dependency Graph support is not enabled; npm audit + Trivy remain mandatory CI security gates.
- Changed the Gitleaks checkout to full history so pull-request scanning can resolve the reviewed commit range.
- Retained multi-architecture `linux/amd64,linux/arm64` image publication and non-root NGINX edge hardening.

# Previous release history

# Release 1.9.11 — CI multi-architecture image hardening

- Fixed Docker CI image scanning on AMD64 GitHub-hosted runners by publishing `linux/amd64` and `linux/arm64` variants under the same immutable commit tag.
- Preserved Oracle ARM64 VM compatibility: the runtime pulls the ARM64 variant automatically.
- Kept Trivy CRITICAL/HIGH image scanning enabled without architecture-specific exceptions.
- Retained v1.9.10 edge non-root hardening and the frontend dependency security fixes.

# Lakhdatar Events v1.9.10 — historical notes

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



## v1.9.18 — Redis authentication and readiness fix

- Removed `spring.data.redis.url` from the application configuration because Spring Boot treats the URL as authoritative and ignores separate host/port/password properties when it is present.
- Configured Redis explicitly through `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, and `REDIS_PASSWORD`, preserving password authentication without embedding secrets in a URI.
- Updated production and development Compose environments to use the explicit Redis connection contract.
- Unified the development backend healthcheck with `/actuator/health/readiness`.
- Disabled Spring Boot's generated in-memory security user auto-configuration because the platform provides its own JWT-based security chain; production logs no longer generate a random development password.

## v1.9.18 deployment-hardening release

- Unified backend readiness on `/actuator/health/readiness` across the container image, production Compose healthcheck, and deploy gate.
- Increased backend readiness allowance to 300 seconds at deploy time and aligned Compose health checks with a 90-second startup period plus bounded retries.
- Added fail-fast detection for exited/dead backend containers and automatic diagnostic capture of Compose state, container health state, and recent backend logs.
- Added equivalent bounded readiness diagnostics for the local edge endpoint before smoke testing.
- Preserved the Flyway safety rule: no automatic image rollback after backend startup.
