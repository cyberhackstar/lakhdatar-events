# v1.9.31 — Enterprise production qualification hardening

## Reliability and payment safety

- Final provider-order recovery path revalidates provider receipt, amount and currency before adoption.
- Provider `NOT_FOUND` remains the only automatic path that clears a stale provider-order reference; transient provider failures stay fail-closed.
- Existing payment idempotency and webhook/reconciliation protections remain intact.

## Operations and scale

- Finance console remains isolated from event/scanner management routes for FINANCE users.
- Operational APIs use cursor pagination and bounded dashboard aggregation.
- Dedicated API/worker deployment mode remains available; background recovery jobs use distributed Redis locks.
- Operations health endpoint remains platform-admin-only.

## Ticket customer experience

- Server-generated, token-protected ticket PDF remains the canonical download format.
- Share-ticket uses the URL fragment for the access credential; Save as PDF downloads an actual PDF and falls back to browser print only if PDF generation fails.

## Load, chaos and enterprise qualification

- k6 suite now includes catalog, burst public-event, single-event reads, checkout provisioning, checkout idempotency, check-in, PDF, operations/finance and SEO/sitemap scenarios.
- Checkout and check-in scenarios use explicit custom metrics so 5xx responses cannot be hidden among expected business conflicts.
- Load-suite execution exports one k6 summary JSON per scenario under `loadtest-results/` for release evidence.
- Added a database invariant verifier for dedicated load-test events (capacity, ticket/order consistency, orphan detection and recovery-state checks).
- Added an enterprise API/flow validation matrix covering public, payment, ticketing, organizer, finance, operations, check-in and HA/DR flows.
- HA/DR remains reference-ready at the application layer; true multi-failure-domain availability requires at least two independent application VMs, external PostgreSQL/Redis HA and executed failover/PITR drills.

## Compatibility

- No existing backend test source has been modified or removed. New tests are additive only.
- No migration is added by this release; V1–V28 remain the schema baseline.
