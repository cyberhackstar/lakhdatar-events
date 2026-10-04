# IMPLEMENTATION STATUS 1.9.35

Release details: `CHANGES-1.9.35.md` and `VALIDATION-1.9.35.md`.

## Current correction
- Fixed the frontend TypeScript compilation defect reported by CI in `ApiService.queryParams()`.
- Preserved the v1.9.34 payment, scanner, CSP and query-sanitization runtime hardening.

---

# v1.9.34 — Deployment qualification correction

- Dedicated worker tier with `WORKER_ENABLED` and distributed-job locks.
- Platform Operations Health API/UI.
- Cursor pagination for events and finance surfaces; existing offset APIs remain compatible.
- SSR host allow-list and Redis TLS configuration support.
- HA edge example mirrors production security/rate-limit headers.
- k6 load scenarios corrected to count business-conflict statuses as expected outcomes.
- Enterprise release qualification, PITR and payment chaos runbooks updated.

# IMPLEMENTATION STATUS 1.9.34

Release details: `CHANGES-1.9.34.md` and `VALIDATION-1.9.34.md`.

## 1.9.31

- Final provider-recovery adoption path now revalidates provider receipt, amount and currency before accepting a recovered provider order.
- Checkout/idempotency/check-in load tests now treat server 5xx responses as hard failures instead of hiding them inside accepted 503/429 status lists.
- Added dedicated k6 scenarios for secure ticket PDF, event operations, finance, robots/sitemap and burst read traffic.
- Load suite exports machine-readable k6 summary JSON for release evidence.
- Added enterprise validation matrix covering customer, payment, ticketing, organizer, finance, operations and event-day flows.
- Added additive contract coverage for HA worker separation, DR/runbook presence and release-level load gates.
- Production HA/DR qualification remains explicitly dependent on external infrastructure and executed failover/PITR drills.

## 1.9.30

- Enterprise finance console with append-only financial ledger (V25) and ledger query index (V27).
- Cursor-based issued-ticket and order APIs for large datasets (V26).
- Server-generated PDF tickets with token-protected access.
- Payment-provider recovery validates provider receipt before adoption.
- Durable distributed job locks remain active across multiple backend replicas.
- HA reference topology supports external PostgreSQL/Redis and multiple stateless application replicas.
- PITR/DR runbook and readiness verifier added.
- k6 catalog, checkout-provisioning and concurrent check-in load scenarios added.
- Payment-provider chaos/resilience test plan added.
- SSR now performs graceful SIGTERM/SIGINT shutdown.
- Finance users are routed only to finance operations; event/ticket/scanner console links are hidden.


- Checkout provider recovery distinguishes confirmed provider NOT_FOUND from transient gateway failures and safely clears stale local provider-order references.
- Top-level Issued Tickets console provides organizer/event-manager scoped search, status/source/event filters and pagination.
- Event operations uses authenticated CSV download and displays report summary metrics.
- Customer ticket UI provides native Share and Save as PDF actions.
- Deployment dotenv parser no longer sources arbitrary `.env` values; Docker Compose remains the authoritative dotenv parser.

- Fixed ticket-link parsing so generated Share / Save as PDF links actually read the URL fragment credential on the ticket page.
- Added bounded admin ticket-search query length validation.
- Expanded organizer team UX copy to document the platform-admin → organizer-owner → manager/staff hierarchy.

## Completed in 1.9.25 (see CHANGES-1.9.25.md)
- Review item 1: reconciliation + provider-order recovery sweeps use a look-back window and `last_reconciled_at` rotation (V24).
- Review item 2: gate scans use a fail-open rate limiter; all other limiters remain fail-closed.
- Review item 3: 30s grace for refresh-token reuse after rotation (no mass revoke inside the window).
- Review item 7: guard rejects placeholder secrets on public-facing boots even without the prod flags.
- New behavioural test: `RateLimitFailOpenTest`.

## NOT completed in 1.9.25 (intentionally)
- **Review item 4 (checkout locks the event row) — deferred.** Switching `findByPublicIdForUpdate` to `FOR SHARE` alone is
  unsafe: Hibernate writes every column on each ticket-type update, which fires the V20 capacity trigger
  (`UPDATE OF event_id, total_quantity`), and that trigger does `SELECT … FOR UPDATE` on the event row. Two checkouts
  holding `FOR SHARE` would then try to upgrade and deadlock. A safe fix needs the trigger changed to fire only when
  those columns actually change (new migration), plus a concurrency test against real PostgreSQL. Not attempted without
  being able to run PostgreSQL here.
- Medium/low review items not touched: verify-replay 401, shared `ld_checkout` cookie, login lockout abuse, MFA for
  ADMIN/FINANCE, refund retry backoff/cap, webhook 503 for unknown orders, `/recover` OTP, static QR, leader lock for
  `@Scheduled` jobs, free-event checkout, and replacing source-text tests with real integration tests.

## Verification status (honest)
- **Not compiled and not run.** Maven Central is unreachable from the packaging sandbox, so `mvn verify` was not run.
- Done: full-backend `javac` parse pass (no syntax errors; only missing-dependency errors), brace/paren balance on every
  edited file, existing source-text contract tests re-read against the edits (strings they assert on are preserved),
  `node tools/verify-platform-baseline.mjs`: PASS.
- **Mandatory before deploy:** `cd backend && mvn -B -ntp clean verify` (Docker needed for Testcontainers), then the
  existing frontend build and smoke tests from the 1.9.23 gate list below.

---
# Previous release: 1.9.23

## Completed in this release
- Enterprise inventory race hardening: parent-event pessimistic lock plus PostgreSQL capacity backstop (V20).
- Reservation referential integrity: nullable legacy-compatible `order_id` FK and supporting index.
- Durable paid-ticket email outbox: bounded worker, retries/backoff, dead-letter state, repair sweep and retention cleanup (V21).
- Cloudinary transaction boundary correction and persisted provider public IDs with compensation (V22).
- Streaming attendee CSV export, bounded QR PNG cache, paginated sitemap shards, and production runtime knobs.
- Existing public API contracts and existing test files are preserved; only targeted additive regression tests were added.


## Verified in packaging sandbox
- `node tools/verify-platform-baseline.mjs`: PASS.
- JSON/package-lock parsing: PASS.
- YAML parsing: PASS.
- `bash -n` for all shell scripts: PASS.
- Java brace/symbol/source-contract checks: PASS.
- Archive integrity: PASS.

## Mandatory release gates not executable in packaging sandbox
- Backend compile/tests: `cd backend && mvn -B -ntp clean verify` (requires Maven + PostgreSQL/Testcontainers Docker).
- Frontend production build/unit tests: `cd frontend && npm ci --no-audit --no-fund && npm run build && npm test` (requires npm registry/cache + ChromeHeadless).
- Multi-architecture Docker build/startup + NGINX smoke test: CI must build linux/amd64 and linux/arm64 images and run the existing regression test.
- Oracle VM public smoke test through Cloudflare remains required.
- Live Cloudinary and payment provider flows remain external-integration tests.

## Operational follow-up
- Real Neelastack logo: replace `frontend/src/assets/neelastack-logo.png` (placeholder shipped).
- Organizer logo for Lakhdatar: upload via Admin -> Organizers after deploy (V18 clears the old placeholder).
- Real production env values cannot be verified from the zip (see ENV-LINKAGE-AUDIT.md checklist).

## Known, deliberately left unchanged
- `SUPPORT_EMAIL` / `SUPPORT_PHONE` remain compatibility configuration values and are not used by core transaction flows.
- Uploading an organizer logo intentionally propagates the organizer branding asset to the organizer's existing brand rows; event-specific override semantics remain unchanged.
- Frontend `siteUrl` remains a build-time value; deployment must build with the canonical public site configuration.
- Cloudinary public-ID cleanup is best-effort. A provider outage during old-asset deletion can leave an unreferenced old asset for later operational cleanup; the newly persisted DB reference remains authoritative.
- Point-in-time recovery (WAL/PITR), immutable off-host backup storage, and live provider integration tests require infrastructure outside the source ZIP and remain deployment-level release gates.
