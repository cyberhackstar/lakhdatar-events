# Enterprise load testing

These tests are intended for a staging environment or a dedicated load-test event. Never use a live payment event for checkout load. The checkout scripts are explicitly blocked against `events.neelastack.com` unless an override is supplied.

## Profiles

- `catalog.js`: public discovery and optional ticket/admin read paths.
- `public-event.js`: high-volume single-event public detail reads.
- `thousands.js`: controlled 1,000-concurrent-user public-read qualification profile; staging/dedicated load-test only.
- `checkout.js`: provider-order provisioning under controlled, disposable inventory.
- `checkout-idempotency.js`: repeated concurrent requests using the same idempotency key; validates deterministic duplicate handling.
- `checkin.js`: concurrent gate-scan pressure with issued QR credentials.
- `ticket-pdf.js`: authenticated ticket PDF generation/download pressure.
- `operations.js`: authenticated event operations, admin health and finance reads.
- `seo.js`: robots and sitemap availability.

## Suggested qualification stages

1. Baseline: 5–10 RPS for 5 minutes.
2. Sustained catalog: 50 RPS for 10 minutes.
3. Burst catalog: 100–250 RPS for 2 minutes, depending on VM/DB sizing.
4. Checkout: ramp from 1 → 5 → 10 RPS only on a disposable staging event/provider setup; 5xx is a hard failure.
5. Scanner: start at 2 scans/sec (below the default 240/minute per-client application limiter), then repeat with multiple source clients or a dedicated staging rate-limit profile for 10 → 25 → 50 scans/sec.
6. Idempotency: concurrent duplicate requests against one dedicated key; 5xx is a hard failure and at least one request must successfully create/return the shared checkout.
7. Ticket PDF: exercise authenticated PDF generation separately because PDF rendering is CPU-bound.
8. Thousands-user qualification: run `thousands.js` only against staging/a dedicated public event; it models approximately one public read/second per active virtual user, validates concurrency/latency, and does not create payment/orders.
9. Operations: exercise cursor-paginated tickets/orders, dashboard/health and finance views.
10. Recovery: inject provider timeout/5xx, then verify no duplicate orders and that reconciliation converges.

During the runs, record NGINX request saturation, p50/p95/p99 latency, HTTP 5xx, PostgreSQL active connections/lock waits, Redis memory/latency, JVM heap/GC, reservation backlog, provider recovery backlog, mail backlog, and CPU/memory.

The qualification gate should require server 5xx < 1%, p95 under the scenario threshold, no oversell, no duplicate ticket issuance, no duplicate refund, no stuck recovery backlog after the run, and no data-integrity violation. Expected business 409/429 responses must be measured separately from server failures rather than being used to hide 5xx rates.

## Run all scenarios

`BASE_URL=https://staging.example.com EVENT_ID=... TICKET_TYPE_ID=... ADMIN_EVENT_ID=... ADMIN_BEARER=... TICKET_ID=... TICKET_TOKEN=... STAFF_BEARER=... CHECKIN_QR_TOKENS=... ENABLE_CHECKOUT_LOAD=true ./run-suite.sh`

For checkout, also set `ENABLE_CHECKOUT_LOAD=true`. For check-in, provide `STAFF_BEARER` and a comma-separated `CHECKIN_QR_TOKENS`.

## Post-run integrity verification

After checkout/load/recovery scenarios finish, run the invariant verifier against the dedicated load-test event:

`DATABASE_URL=... LOADTEST_EVENT_ID=... ./verify-invariants.sh`

The verifier fails on capacity violations, ticket/order mismatches, ticket orphans/event mismatches, expired held reservations left behind, duplicate provider-order references, or captured payments without issued tickets. Never run it against an active production event.

The GitHub Actions load-test workflow stores the k6 summary JSON files as a CI artifact. Preserve them together with the commit SHA, environment sizing and test-event identifier as release evidence.
