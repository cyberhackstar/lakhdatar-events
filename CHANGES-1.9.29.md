# v1.9.29 — Enterprise scale and production operations

## Reliability and payments

- Provider-order recovery now validates provider order receipt, amount and currency before adoption.
- Distributed scheduler locks protect every state-changing recovery/sweep job when multiple backend replicas run.
- Added operational counters for stale payments, reservations, mail failures and webhook backlog.

## Finance

- Added finance-scoped operational console for platform administrators and authorized organizer finance roles.
- Added append-only financial ledger entries for sales and refunds with immutable database enforcement.
- Added paginated ledger and refund queries with organizer server-side scope.

## Scale

- Added cursor pagination APIs for all issued tickets and event-scoped tickets/orders.
- Added database indexes for cursor and ledger access paths.
- Dashboard aggregate query consolidated to reduce repeated aggregate scans.

## Ticket experience

- Added token-protected server-generated PDF ticket endpoint.
- Customer ticket/payment/recovery pages support Share ticket and Save as PDF with browser-print fallback only when PDF generation/download is unavailable.

## Infrastructure / DR

- Backend production Compose allows external DB/Redis endpoints without breaking the single-VM defaults.
- Added multi-VM HA reference architecture.
- Added PostgreSQL PITR/WAL requirements and readiness verification runbook.
- Added Prometheus alert examples for uptime, latency, 5xx and DB-pool pressure.
- Added graceful SSR shutdown for rolling deployments.

## Performance testing

- Added k6 catalog/read, checkout-provisioning and concurrent check-in scenarios.
- Added manual GitHub staging load-test workflow.
- Added provider timeout/duplicate/out-of-order webhook/refund chaos test plan.

## Compatibility

- Existing test source remains intact; changes are additive except for intentional production source/config hardening.
- Existing single-VM Docker Compose defaults continue to work.
