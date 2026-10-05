# Neelastack Events v1.9.50 — API & Flow Audit

This report is source-derived from the packaged application. It inventories controller routes, frontend API calls, authorization boundaries, payment/check-in/recovery flows, and observability wiring.

## Public flows
- Event discovery: `/api/v1/public/events`, `/search`, `/featured`, `/upcoming`, `/facets`, `/{slug}`.
- Checkout: `/api/v1/public/checkout` creates an order, holds inventory and provisions the provider order.
- Payment verification: `/api/v1/public/checkout/verify` is server-authoritative and idempotent. Cashfree status is obtained from the provider order's payment list.
- Recovery: `/api/v1/public/orders/recover` resolves an `LK-...` order number or a provider transaction ID + original checkout email; issued tickets are tokenized and non-issued/closed tickets are filtered.
- Ticket access/PDF: `/api/v1/public/tickets/{id}` and `/pdf` require the ticket access token and send `no-store`.

## Auth/admin flows
- Authentication: login, refresh, logout, password-status, change-password and invitation acceptance.
- Event lifecycle: create → draft → readiness → publish → unpublish/cancel/complete/archive. Publish is row-locked server-side, permission-checked and idempotent.
- Event data: detail, update, ticket type CRUD, staff, managers, team, operations, ticket/order cursors and authenticated CSV.
- Finance: overview, ledger/refunds cursor and offset queries with scoped authorization.
- Check-in: authenticated gate/event authorization → QR validation → row lock → single-use transition.

## Provider flows
- Cashfree: create order → hosted redirect → server-side payment fetch → webhook signature verification → reconcile/issue tickets/refund compensation.
- Razorpay: order creation → client callback signature verification → server payment fetch → reconcile/issue tickets/refund compensation.
- Provider webhook processing is persisted/idempotent before business reconciliation.

## Observability flows
- Spring Actuator → Prometheus.
- Spring Micrometer tracing → OTLP/Tempo.
- Tempo span-metrics/service-graphs → Prometheus remote-write.
- Docker logs → Alloy → Loki.
- Edge/host/container/database/Redis exporters → Prometheus.
- Public site/API → Blackbox → Prometheus → Alertmanager.

## Concrete hardening completed in this release
1. Provider payment/transaction IDs are now durable across successful, failed, cancelled and pending Cashfree attempts.
2. Tempo metrics generation now has an actual remote-write destination and Prometheus accepts remote writes.
3. Synthetic public-site alerting no longer conflates robots/sitemap failures with the primary public site outage.
4. Container restart alert no longer depends on a non-existent restart counter metric.
5. Business observability refresh failures are now visible to alerting.
6. OTLP trace export is explicitly wired to the private Tempo endpoint.

## Remaining deployment-gated checks
- Run full GitHub Actions Maven + Angular CI.
- Run the production compose observability stack and validate Grafana/Prometheus/Loki/Tempo health from the VM.
- Execute real Cashfree/Razorpay sandbox or production-safe smoke tests before enabling customer traffic.

## Final audit findings
- Frontend API client and backend controller surfaces were cross-checked; all customer/admin API calls used by the Angular client map to backend routes, with authentication methods intentionally kept in AuthService.
- Recovery tokens are now issued only for confirmed orders, reducing the blast radius of any inconsistent cancelled/pending order state.
- Cashfree reconciliation no longer chooses an arbitrary captured transaction when multiple captured references exist; a matching provider transaction/local authoritative ID is preferred, otherwise the system requires explicit reconciliation rather than issuing tickets against an ambiguous payment.
- The public event page now displays the actual configured payment provider instead of hard-coding Razorpay.
- Admin event-list publishing now disables the action for invalid lifecycle/past-start states while retaining server-side readiness validation as the source of truth.
- Grafana SRE dashboard schema and public synthetic SLI reference were corrected; the dashboard now targets the dedicated public-site recording rule.
- Flyway V31 creates durable payment-attempt history without a redundant provider-payment index.
