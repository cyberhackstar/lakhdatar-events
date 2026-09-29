# v1.4.9

## CI/CD deployment model aligned with Neelastack production

- Replaces `ORACLE_SSH_KEY`, `ORACLE_SSH_HOST`, `ORACLE_SSH_USER`, and `ORACLE_GHCR_TOKEN` with the proven deployment secret contract: `DEPLOY_HOST`, `DEPLOY_SSH_KEY`, and `DEPLOY_KNOWN_HOSTS`.
- `DEPLOY_HOST` contains the full SSH destination (`user@host`) and strict host verification uses the supplied `DEPLOY_KNOWN_HOSTS` value instead of `ssh-keyscan` at deploy time.
- Deployment now fast-forwards/resets the VM repository to the exact release SHA and runs the existing production deploy script in place, matching the previous Neelastack deployment topology.
- Removes the separate VM GHCR PAT requirement. The workflow uses the short-lived GitHub Actions `GITHUB_TOKEN` to log the VM into GHCR for the deployment and logs out afterward.
- Hardens manual rollback with an explicit executable-bit step before invoking `rollback.sh`.
- Keeps public-health rollback, but only invokes it after a successful deployment step; application-level deployment failures are handled by `infra/deploy/deploy.sh`.
- Bumps release metadata to `1.4.9`.

# v1.4.8

## Frontend production build configuration hardening

- Fixed the Angular production build path so CI/container builds explicitly use the `production` configuration.
- Set the Angular build target default configuration to `production` to prevent accidental development environment replacement in release builds.
- This prevents `http://localhost:8081/api/v1` from being compiled into production browser bundles; production uses the same-origin `/api/v1` endpoint.

# Lakhdatar Events 1.4.7

## Integration-test fixture correction

- Fixes `MultiEventCatalogIntegrationTest.lifecycleTransitionsAreGuarded`, which used hard-coded actor ID `1` without creating the corresponding `users` row.
- Creates a real ADMIN test user and uses its persisted database ID for lifecycle/audit operations.
- Keeps the production audit-log foreign-key integrity constraint intact; no authorization or production security rule is weakened.
- Bumps release metadata to `1.4.7`.

## CI validation

- v1.4.6 frontend baseline verification and production build passed.
- v1.4.6 frontend unit tests reached the Karma/ChromeHeadless stage successfully.
- v1.4.6 backend CI failed only in `MultiEventCatalogIntegrationTest.lifecycleTransitionsAreGuarded` because `audit_logs.actor_user_id=1` had no matching `users` row.
- v1.4.7 corrects the test fixture so the database contract is exercised with a valid actor.

# Lakhdatar Events 1.4.6

## Backend manager-ticket contract stabilization

- Aligns `ManagerTicketService` authorization source usage with the existing event-manager assignment contract.
- Keeps event assignment authorization before the event row is locked and before event-state checks.
- Fixes the failing `ManagerTicketContractTest.managerIssuanceRequiresEventAssignment` contract assertion without weakening authorization.
- Bumps release metadata to `1.4.6`.

## Validation

- Frontend Angular production/SSR build: confirmed on the developer machine in v1.4.5.
- Frontend unit tests: 4/4 successful in v1.4.5.
- Neelastack stability baseline: PASS in v1.4.5.
- Backend `mvn clean verify` on v1.4.5 reached 56 tests with 55 passing and 1 contract-test failure; v1.4.6 fixes that exact contract mismatch.

# Lakhdatar Events 1.4.5

## Frontend test-target stabilization

- Adds the Angular `@angular/build:unit-test` target with Karma/Jasmine, matching the dependencies already declared in the project.
- Adds a deterministic headless test command so `npm run test` works in local and CI environments.
- Adds the missing `tsconfig.spec.json` and a small production-code unit test suite covering formatting, URL safety, and booking-state helpers.
- Keeps the Angular 20 / Node 22 Neelastack-aligned runtime baseline unchanged.
- Keeps `verify:baseline` executable through the package script; run it from `frontend` with `npm run verify:baseline`.

## Validation notes

- Angular production/SSR build was confirmed on the developer machine for v1.4.3 before this test-target-only release.
- v1.4.5 removes an unsupported `--no-progress` test flag and removes the unsupported test-target progress option; the Karma test command now matches the Angular 20 unit-test builder.

# Lakhdatar Events 1.4.3

## SSR route-extraction stability fix

- Explicitly loads `zone.js` in the browser bootstrap entry.
- Explicitly loads `zone.js/node` in the Angular SSR bootstrap entry.
- Fixes Angular `NG0908` during SSR route extraction when Zone.js is installed but not statically imported.
- Keeps Angular 20 / Node 22 Neelastack-aligned dependency baseline unchanged.

# Lakhdatar Events 1.4.2

## Phase 5.2 — Angular build stabilization
- Fixes the Angular 20 production build failures in `EventEditorComponent`.
- Normalizes new-ticket creation to the same `Observable<void>` contract used by ticket updates, eliminating the RxJS union subscription type error.
- Routes the `publish` transition through the existing dedicated publish API while keeping the other event transitions event-scoped.
- Removes the unused `DatePipe` import from the event editor.
- Hardens the frontend dependency refresh script so failed `npm install`, `npm ci`, baseline verification, or production builds cannot be reported as successful.
- Regenerates `frontend/package-lock.json` from the Angular 20 baseline and verifies it with a clean `npm ci`.

## Validation
- Angular dependency tree resolves on Angular 20.3.x with Node 22.
- The supplied build errors are patched; baseline JSON/lockfile validation passes. The full Angular build still needs to be rerun on the development machine/CI.

# Lakhdatar Events 1.4.1

## Phase 5.1 — Stability baseline and Angular build tooling
- Keeps the event platform on the same overlapping Neelastack framework/runtime baseline rather than introducing a separate dependency train.
- Frontend declarations match the supplied Neelastack package baseline: Angular runtime 20.3.30, Angular SSR/build/CLI 20.3.36, Express 4.22.2, RxJS 7.8.x, tslib 2.8.x, zone.js 0.15.x, TypeScript 5.9.x and the `qs` 6.16.0 override.
- Backend shares the Neelastack baseline where applicable: Spring Boot 4.0.8, Java 21, JJWT 0.13.0, PostgreSQL JDBC 42.7.12, Testcontainers 1.21.4 and Spring Boot MVC/Jackson/Flyway starters.
- Uses the modern Angular `@angular/build:dev-server` builder for local development, matching the application builder family.
- Adds CI enforcement of the declared Neelastack baseline before dependency installation.
- Uses Node 22 as the runtime/CI major-line baseline, matching the Neelastack CI configuration.
- Fixes the baseline verification script so it resolves the repository root from its own location rather than depending on the current working directory.

## Validation note
- The `frontend/package-lock.json` is regenerated from the Angular 20 baseline and is checked with `npm ci` in this release.

# Lakhdatar Events 1.4.0

## Phase 5
- Aligns overlapping frontend/backend dependencies with the supplied Neelastack stability baseline.
- Adds the Neelastack baseline verification script and documentation.
- Adds a mobile-first event-card view for the admin portfolio while retaining the desktop operations table.
- Normalizes user-visible Neelastack branding.
- Fixes refund authorization so EVENT_MANAGER is never treated as a financial refund approver.
- Updates the Angular SSR Docker runtime to install production dependencies, matching the Neelastack container pattern.
- Keeps the iOS 16px input/viewport stability guard.

## Stability / dependency note
- `frontend/package.json` is aligned to the requested Neelastack versions.
- The bundled `frontend/package-lock.json` is generated from the Angular 20 graph and is intended for clean `npm ci`/CI consumption. See `docs/NEELASTACK-VERSION-BASELINE.md`.
- CI now checks the declared Neelastack baseline before installation.

# 1.2.0: Event-manager scope, complimentary issuance and mobile stability

- Added explicit event-manager assignments so `EVENT_MANAGER` users can operate and scan only assigned events. Organizer membership alone no longer grants event-manager-wide access.
- Added manager provisioning/assignment controls and event-scoped complimentary ticket issuance. Complimentary tickets consume inventory, create zero-value confirmed orders, record issuer provenance, and appear with a `COMPLIMENTARY · MANAGER ISSUED` badge.
- Scanner responses now surface ticket provenance/issuer information; customer ticket views do the same.
- Hardened scanner authorization ordering to avoid event-state probing.
- Fixed iOS/Safari form focus zoom by keeping editable controls at 16px or larger without disabling user zoom.
- Improved catalogue event sales-state calculation to respect ticket-specific sale windows.
- Optimized manager event retrieval with a direct event-assignment query.
- Made direct production deployment load `IMAGE_NAMESPACE` from `/home/ubuntu/apps/lakhdatar-events/.env`.
- Added event-manager authorization, issuance provenance and mobile viewport regression contracts.
- Event cancellation now invalidates issued tickets immediately and asynchronously queues eligible paid-order refunds through the existing retry/recovery pipeline.

# 1.1.0: Multi-event platform

See docs/multi-event.md. Adds the multi-event catalogue, Angular SSR with SEO, organizer-aware admin lifecycle, edge/web/backend deployment on port 4002 and migration V10.

# Lakhdatar Events 1.0.13

## Production merge / regression fix
- Merged the full 1.0.12 hardening set with the previously verified 1.0.11 Windows/Maven classpath fix.
- Provider-order recovery remains scheduled and retryable, but now executes from `OrderService`; the standalone `ProviderOrderRecoveryJob` class is removed to prevent the observed stale-classpath failure during Spring configuration scanning.
- Retained 1.0.12 protections: refresh-token reuse revocation, login timing equalization, webhook state regression protection, production secret guards, JPA new-entity version handling, shared Testcontainers infrastructure, bounded Surefire memory, and reproducible npm installs.
- Release version is aligned across `VERSION`, Maven, frontend `package.json`, and `package-lock.json`.

## Deployment posture
- ARM64 OCI images remain immutable and are deployed by Git commit SHA.
- Production database migrations remain Flyway-managed with PostgreSQL as the source of truth.
- Deployment keeps pre-deploy database backups, frontend/backend health gates, and image rollback support.

---

# Lakhdatar Events 1.0.12

## Fixes
- **Auth: refresh-token reuse detection now actually works.** `AuthService.refresh` revoked every active session on token reuse and then threw an `ApiException`; the surrounding `@Transactional` rolled the revocation back. Now `@Transactional(noRollbackFor = ApiException.class)`.
- **Auth: login timing no longer reveals whether an email exists** (one BCrypt comparison is always performed).
- **Webhooks: a late `payment.authorized` event can no longer overwrite a captured/refunded payment.** The status change now happens under a row lock and only from pre-authorization states.
- **Production guard:** startup now refuses the default/short `DB_PASSWORD` in production.
- **Entities:** `Ticket.version` / `TicketType.version` start as `null` so Spring Data uses `persist()` instead of `merge()` for new rows.
- **Frontend:** fixed `TS2322` (`EventView | null` vs `undefined`) in `checkout.component.ts` that failed `ng build`; removed an unused `CurrencyPipe` import (NG8113).

## Build / test infrastructure
- The two Testcontainers tests now share one PostgreSQL container and one Spring context (`AbstractPostgresIntegrationTest`) instead of starting two of each. They are tagged `integration` and skipped automatically when Docker is unavailable.
- Surefire's forked JVM is memory-bounded (`-Xmx768m`) and test output goes to `target/surefire-reports`.
- `mvn -B -ntp clean verify -Punit` runs only the fast unit/contract tests (no Docker).
- Frontend installs are reproducible: `package-lock.json` added; CI and the Dockerfile use `npm ci`.
- See `docs/troubleshooting-windows-build.md` for the `NoClassDefFoundError` / `insufficient memory` failure seen on Windows + OneDrive + Docker Desktop.

## Validation performed for this release
- Frontend: `npm install` and `ng build --configuration production` succeed.
- Backend: all Java sources parse with `javac` (no syntax errors). A dependency-resolved `mvn clean verify` was **not** run for this release (Maven Central was unreachable from the build sandbox) - run it locally or in CI before deploying.

---

# Lakhdatar Events 1.0.10

## Current release
- Fixed `CheckInConcurrencyTest` to pass the event public `UUID` required by `CheckInService.ScanRequest`, eliminating the `Long cannot be converted to UUID` test-compilation error.
- Preserved the corrected Testcontainers JUnit 5 import and prior Phase 3 backend hardening.

## Validation limits
- Full dependency-resolved `mvn clean verify` must still be executed on a machine with the project dependencies available and Docker running for Testcontainers.

## Previous release history

# Lakhdatar Events 1.0.10

Bugfix: corrected CheckInConcurrencyTest to pass the event public UUID required by CheckInService.ScanRequest.

# Lakhdatar Events 1.0.9

## Bugfix release
- Corrected Testcontainers JUnit 5 annotation imports in concurrency integration tests.
- Preserved existing Testcontainers dependencies in Maven test scope.
- Release metadata synchronized to 1.0.9.

## Phase 3 compiler/test hardening

- Fixed RefundService lambda capture compilation failures.
- Removed unsafe amount-only refund recovery fallback; recovery now requires provider receipt identity.
- Fixed CheckInConcurrencyTest captured-user-id compilation issue.
- Converted RefundIdempotencyContractTest into an executable JUnit test.
- Made scheduled refresh-token cleanup explicitly transactional.
- Kept project version metadata aligned at 1.0.8.

## Verification limits

This workspace cannot execute the dependency-resolved Maven/Testcontainers build or Docker runtime. Run `mvn -B -ntp clean verify` on the development/CI machine and provide the full result before deployment.
