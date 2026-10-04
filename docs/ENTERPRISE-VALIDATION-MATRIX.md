# Enterprise API and flow validation matrix — v1.9.33

This matrix is the release-review index. Static/source validation confirms endpoint presence, authorization wiring and core invariants; CI/staging must execute the runtime gates listed in `docs/ENTERPRISE-RELEASE-QUALIFICATION.md`.

## Customer flow

| Flow | API/route | Required outcome |
|---|---|---|
| Browse | `/api/v1/public/events/**` | only published catalog data; bounded responses |
| Event detail | `/api/v1/public/events/{slug}` | 200 for published event; 404 otherwise |
| Checkout | `POST /api/v1/public/checkout` | reservation + local order; provider session only after safe recovery check |
| Idempotent checkout | same idempotency key | one logical order/provider order; deterministic reuse/conflict |
| Payment verify | `POST /api/v1/public/checkout/verify` | server/provider verification; never trust browser success alone |
| Recovery | `POST /api/v1/public/orders/recover` | retrieve already-issued ticket or reconcile uncertain state |
| Ticket | `/api/v1/public/tickets/{id}` | token required; access credential is never logged |
| Ticket PDF | `/api/v1/public/tickets/{id}/pdf` | token required; `application/pdf` |
| Share | `/ticket/{id}#access=...` | credential stays in fragment; frontend sends `X-Ticket-Token` |

## Payment and financial flow

| Flow | Controls |
|---|---|
| Provider order | receipt/order-number binding, amount/currency validation, provider lookup before recreation |
| Provider timeout | `RECOVERY_PENDING`; no blind duplicate creation |
| Provider NOT_FOUND | stale local provider reference may be cleared and safely recreated |
| Webhook | signature verification, idempotent event record, provider/order/payment matching |
| Refund | role + event scope + idempotency + provider/local reconciliation |
| Financial ledger | append-only V25 trigger; organizer-scoped finance reads |

## Organizer/admin flow

| Flow | Required scope |
|---|---|
| Platform admin | all organizers/events/financial operations |
| Organizer owner | own organizer and its events/team |
| Event manager | assigned events only |
| Finance | finance console only; no event/scanner management links |
| Gate staff | assigned event/gate operations only |
| Issued tickets | organizer/event scoped cursor pagination |
| Orders | organizer/event scoped cursor pagination |
| Operations | authenticated summary/ticket/order reads |
| Ops health | ADMIN only |

## Event lifecycle

`DRAFT → PUBLISHED → STARTED → COMPLETED → ARCHIVED`, with cancellation/unpublish rules enforced server-side. Completion before event start must remain a 409 business conflict, not be relaxed to 200.

## Event-day flow

`STAFF → scan → token verification → event/staff scope → ticket row lock → status transition → audit record`.

Duplicate/replay scans remain business conflicts; a Redis outage must not turn into a ticket-authentication bypass.

## Async/recovery flow

Reservation expiry, payment reconciliation, provider-order recovery, refund recovery, webhook recovery and durable mail delivery are worker-gated and protected by distributed Redis locks so multiple replicas do not intentionally execute the same scheduled sweep.

## HA/DR flow

Two independent application VMs → health-checked ingress → external/managed PostgreSQL with standby/PITR → Redis HA → at least two ingress/tunnel connectors. Application nodes remain stateless; PostgreSQL is the business source of truth.

## Load scenarios

- `catalog.js`: 5→10→25→50 RPS public catalog.
- `burst.js`: configurable 50→100→200→250 RPS event-detail burst.
- `public-event.js`: sustained single-event read traffic.
- `checkout.js`: 1→2→5→10 RPS staging provider sandbox; server 5xx hard-fail metric.
- `checkout-idempotency.js`: concurrent duplicate requests on one key; no 5xx and at least one 2xx result expected.
- `checkin.js`: default 2 RPS per client to stay below the default 240/minute limiter; scale with multiple source clients or an explicit staging limiter profile.
- `ticket-pdf.js`: authenticated PDF generation pressure.
- `operations.js`: event summary, issued tickets, orders, operations health and finance reads.
- `seo.js`: robots/sitemap availability.

Runtime certification requires preserving the k6 JSON summaries and confirming no duplicate tickets/provider orders, no oversell, no financial mismatch, and no recovery backlog outside SLA after each scenario.
