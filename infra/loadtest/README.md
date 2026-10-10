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

## Automatic staging qualification (recommended)

GitHub Actions `Staging Load Test` and `Enterprise Release Qualification` call
`node ./infra/loadtest/auto-runner.cjs`. It reuses `E2E_STAGING_PROVISIONING_CONFIG`, creates a new
`loadtest-<run-id>` event with large disposable inventory, generates every required ticket/staff/admin
credential and QR token, maps them into k6 without saving them as secrets, runs the selected scenario,
checks database invariants over the existing pinned SSH connection, then cancels the event and
_deactivates_ the generated team accounts. No `LOADTEST_*` fixture secrets or DB URL are needed.

The protected `staging` environment must already contain `E2E_STAGING_PROVISIONING_CONFIG` and the
existing `STAGING_DEPLOY_HOST`, `STAGING_DEPLOY_SSH_KEY`, `STAGING_DEPLOY_KNOWN_HOSTS` secrets. Do not
pass `STAGING_ENV_FILE` or database credentials to the workflow. The backend's payment-safety
preflight must independently confirm test/sandbox mode before any fixture is created.

Public-only scenarios (`catalog.js`, `burst.js`, `seo.js`, `thousands.js`) can run without fixture
provisioning. `burst.js` uses the featured endpoint if no `EVENT_SLUG` is supplied. Stateful tests
provision fixtures automatically.

## Post-run integrity verification

For every stateful automated scenario, the wrapper queries consistency inside `lakhdatar-staging-postgres`
using the existing pinned SSH connection and the generated event UUID. It does so even if k6 reports an
SLO failure and runs before fixture cleanup. The SQL verifier fails on inventory/capacity violations,
ticket/order mismatches, ticket orphans/event mismatches, expired held reservations left behind, duplicate
provider-order references, confirmed orders with missing tickets, or captured payments without issued tickets.

Local/dev runs may still use `DATABASE_URL=... LOADTEST_EVENT_ID=... bash ./infra/loadtest/verify-invariants.sh`;
GitHub Actions intentionally do not provide a database URL or DB password to the runner.

## Enterprise staging gate

The `enterprise-gate` scenario automatically provisions fixture IDs and credentials; it fails closed if
the provisioner does not supply a required field, and reports the missing field names without exposing
credential values. The orchestration verifies database invariants and requires teardown to succeed.
Never point this gate at the live site or enable production checkout load.

## Enterprise hot-sale qualification

Use a dedicated staging event and payment-provider sandbox. Never point this test at production.

```bash
ENABLE_ENTERPRISE_CHECKOUT_LOAD=true \
CHECKOUT_TARGET_RATE=100 \
BASE_URL=https://staging.example.com \
EVENT_ID=<staging-event-id> \
TICKET_TYPE_ID=<staging-ticket-type-id> \
./infra/loadtest/run-suite.sh
```

This exercises the expensive checkout path at a configurable arrival rate and accepts only explicit
business/overload responses (`200/201/409/429/503`) as contract-valid. A `503` must be caused by
intentional capacity protection or provider degradation, not hidden server errors; inspect the exported
k6 report and application metrics before certifying a release.
