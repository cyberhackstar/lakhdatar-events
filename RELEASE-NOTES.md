# v1.9.47

- Fixed the CI false negative in `EnterpriseScaleContractTest` by normalizing whitespace in formatting-sensitive behavioral assertions.
- Replaced the platform Neelastack logo asset with the supplied transparent horse + wordmark artwork.
- Preserved the 48px shared Neelastack header mark and previous production checkout/recovery/CSV safeguards.

See `CHANGES-1.9.47.md`, `VALIDATION-1.9.47.md`, and `PRODUCTION-DEPLOY-1.9.47.md`.

---

# v1.9.45

- Corrected strict TypeScript event-end narrowing in the admin event editor.
- Corrected backend contract-test fixture resolution for CI working directories.
- Globally configured Zone.js for Angular unit tests.
- Retained payment, scanner, CSV, finance, event lifecycle, multi-day booking, and ticket-count protections from v1.9.39.

See `CHANGES-1.9.45.md`, `VALIDATION-1.9.45.md`, and `PRODUCTION-DEPLOY-1.9.45.md`.

---

# v1.9.44

- Fixed the Angular frontend unit-test CI failure (`NG0908`) by explicitly loading `zone.js` and `zone.js/testing` before TestBed specs.
- Retained the `EventQuery` regression contract for optional filters and `false`/`0` values.
- Retained the v1.9.34 payment/scanner/CSP/admin-query runtime fixes and the v1.9.35 query sanitizer typing correction unchanged.

See `CHANGES-1.9.44.md` and `VALIDATION-1.9.44.md`.

---

# v1.9.34

- Cashfree checkout reliability and Promise/result handling fixed.
- Deterministic third-party SDK loader with timeout/recovery added.
- Gate scanner keeps its camera session alive between scans and shows immediate server verification state.
- Optional admin/operations/finance query parameters are sanitized centrally.
- Cloudflare Web Analytics beacon CSP source allowed.
- Added regression tests and baseline guards.

## Previous release: v1.9.33

## v1.9.33 — Deployment qualification correction

- Fixed Flyway `V26__enterprise_cursor_indexes.sql`: the `tickets` table has `created_at`, not `issued_at`; V26 now contains only the valid event-order cursor index.
- Kept ticket cursor indexes on the authoritative `tickets.created_at` ordering in V28 and removed the stale `issued_at` reference from migration documentation.
- Hardened migration regression coverage so an invalid `issued_at` cursor index cannot return.
- Corrected implementation-contract tests that were coupled to an outdated AdminService repository-call string while the runtime authorization path already enforced `event_manager_assignments`.
- Made the issued-ticket organizer console's default scope explicit as **All events** (within the caller's authorized organization/event scope).
- Corrected the release qualification contract to accept the properly escaped production hostname guard used by the k6 idempotency test.
- Bumped runtime/package metadata to v1.9.33; no new database migration was added.

See `CHANGES-1.9.33.md` and `VALIDATION-1.9.33.md`.

---

# Release Notes — v1.9.32

## v1.9.32 — Compilation and release-gate correction

- Fixed `FinanceService` compilation by importing the shared `Enums` type used by cursor refund validation.
- Fixed `OperationsHealthService` compilation by explicitly selecting the `RedisCallback` overload for `RedisTemplate.execute(...)`.
- Fixed Angular `OperationsHealthComponent` control-flow syntax and removed the unused `DecimalPipe` import.

See `CHANGES-1.9.32.md` and `VALIDATION-1.9.32.md`.

---

# Release Notes — v1.9.31

## v1.9.31 — Enterprise production qualification hardening

This release completes the remaining application-level enterprise hardening from v1.9.28–v1.9.30: provider-recovery defense-in-depth, production-grade k6 scenario coverage with hard 5xx gates, authenticated PDF/operations/finance load scenarios, distributed worker separation, cursor-scaled operational queries, HA/DR reference deployment, observability and chaos/restore qualification runbooks.

The source package is HA-ready, but live BookMyShow-class availability remains an infrastructure claim that requires at least two independent application VMs, external PostgreSQL HA/PITR, Redis HA, health-checked ingress and executed failure/load/restore evidence.

See `CHANGES-1.9.31.md` and `VALIDATION-1.9.31.md`.

---

# Release Notes — v1.9.30

## v1.9.30 — Enterprise scale completion

This release extends the v1.9.28/1.9.29 hardening with a dedicated worker tier, operator health console, keyset/cursor pagination for high-volume management and finance views, Redis TLS/SSR host hardening, and a HA edge reference that retains the production security controls.

Production availability still depends on the target infrastructure: two VMs, external HA data services, off-host PITR, health-checked ingress, and executed load/chaos/restore drills.

# v1.9.29 — Enterprise scale + finance + PDF + HA/DR + load testing

This release completes the organizer operations and customer-ticket workflow hardening. It preserves the platform-admin → organizer → team → event-scoped access model, keeps payment recovery fail-closed except for explicit provider NOT_FOUND states, provides server-enforced issued-ticket visibility, and adds reliable Share / Save as PDF actions from the generated ticket, payment result, and recovery screens. The ticket page now correctly reads the access credential from the route fragment, preventing shared/PDF links from appearing invalid after opening.

See `CHANGES-1.9.29.md` and `VALIDATION-1.9.29.md`.

---

# v1.9.27 — Organizer operations + checkout recovery

This release hardens payment-order recovery so a stale provider-order reference can be safely recreated only after the provider explicitly returns NOT_FOUND; transient provider failures remain fail-closed. It adds an organizer/platform issued-ticket console with server-enforced scope, event filtering, search and pagination, and keeps event-scoped Orders/Issued Tickets operations. Ticket customers receive explicit **Share ticket** and **Save as PDF** actions; mobile uses the native share sheet and desktop falls back to clipboard. The deployment script no longer sources the complete `.env`, preventing valid dotenv values such as `MAIL_FROM="Neelastack Events <events@neelastack.com>"` from breaking Bash deployment. Existing role hierarchy and tests are preserved.

See `CHANGES-1.9.26.md` and `VALIDATION-1.9.27.md`.

---

# v1.9.25 — Production release hardening

This release closes the v1.9.24 production blockers: public sitemap shard authorization and bounds, plus event publish/update parent-row locking. No public API payload contracts were intentionally changed and no existing test files were modified.

See `CHANGES-1.9.25.md` and `VALIDATION-1.9.25.md`.

---

# v1.9.23 — Enterprise hardening

v1.9.25 is a production-hardening release focused on event inventory correctness, durable ticket-email delivery, media transaction boundaries, large-export performance, QR caching, scalable sitemaps, database integrity, and explicit runtime tuning. Existing v1.9.22 APIs and regression tests are retained; targeted regression coverage was added.

See `CHANGES-1.9.23.md` for the complete change set.

---

# Lakhdatar Events v1.9.22 — organizer management, PNG logo, env wiring

- New: `GET/POST /api/v1/admin/organizers` and an Organizers admin page. Organizer name and logo are entered at creation; the logo goes to Cloudinary and only its HTTPS URL is stored. ADMIN only for creation.
- Fixed: event creation sent no organizer, failing with ORGANIZER_REQUIRED once two organizers existed. The create form now has an organizer selector.
- Removed hardcoded Lakhdatar branding (default organizer logo, pre-filled setup form, bootstrap logo) and `DEFAULT_ORGANIZER_LOGO_URL` / `NEELASTACK_LOGO_URL`.
- Neelastack logo is now `frontend/src/assets/neelastack-logo.png` (placeholder shipped; replace the file). Brand view always uses the bundled logo, V18 migrates old rows, `/assets` cache is 10 minutes.
- Fixed: `JWT_ACCESS_TOKEN`, `TICKET_VIEW_TTL`, `CHECKIN_EARLY_WINDOW`, `RAZORPAY_BASE_URL`, `PAYMENT_RECONCILIATION_*` were documented but never passed to the backend container. Dev compose now passes setup, Cloudinary and Cashfree variables.
- Fixed misleading JWT error text and a broken image when an organizer has no logo.
- Version 1.9.22 in VERSION, pom.xml, package.json, package-lock.json.

# Lakhdatar Events v1.9.21 — edge forwarded-IP correction (fixes failed v1.9.20 smoke test)

- **Root cause of the `Smoke test failed: http://127.0.0.1:4002/ (HTTP 400)` deployment failure:** `edge/nginx.conf` fell back to `$binary_remote_addr` when no `CF-Connecting-IP` header was present, and forwarded that value upstream as `X-Real-IP` / `X-Forwarded-For` / `CF-Connecting-IP`. `$binary_remote_addr` is 4 (IPv4) or 16 (IPv6) raw bytes, which are illegal in an HTTP header; Node's HTTP parser answers `400 Bad Request` with an empty body before Express/Angular ever run (hence no `Cache-Control` in the logged headers and an empty body). The deployment smoke test calls the loopback port directly, so it never carries a Cloudflare header and always hit the fallback. Real Cloudflare traffic was unaffected.
- The forwarded client key is now always printable text: a validated `CF-Connecting-IP` (IPv4/IPv6 literal) or `$remote_addr`. Rate-limit zones, logs and upstream headers all use it.
- `tools/verify-platform-baseline.mjs` now fails CI if `$binary_remote_addr` is ever forwarded in a header again.
- Verified with the real Angular SSR build behind real NGINX: old config -> 400 without CF header; new config -> 200 with and without it.
- Because Flyway had already migrated before the smoke test failed, the v1.9.20 containers are running; deploying v1.9.21 over them is safe (forward-only, no schema change in this release).

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


## Current release
See `CHANGES-1.9.47.md` and `VALIDATION-1.9.47.md`.

## v1.9.27
Checkout recovery, organizer/event operations views, issued ticket visibility, and customer Share/Save PDF actions.
