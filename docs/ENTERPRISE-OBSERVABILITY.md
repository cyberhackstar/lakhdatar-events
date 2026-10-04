# Enterprise observability

Production should monitor application, data, dependency and business-integrity signals separately.

## Required signals

- HTTP request rate, p50/p95/p99 latency, 4xx and 5xx by route.
- PostgreSQL connection-pool usage, active connections, lock waits, slow queries and replication/PITR lag.
- Redis latency, memory, evictions, replication/failover state.
- JVM heap, GC pauses, thread-pool saturation and container CPU/memory.
- Payment recovery-pending count, webhook backlog/stuck count and refund pending/failed count.
- Reservation-held/expired counts and ticket issuance/check-in rates.
- Mail pending/failed/DLQ counts.

## Business integrity alerts

Alert on: successful provider payment without a local capture after the reconciliation SLO; captured payment without the expected ticket count; duplicate webhook volume spikes; refund provider/local-state mismatch; ticket/check-in integrity violations; and reconciliation queues older than the defined SLA.

## Correlation

Preserve `X-Correlation-ID` across NGINX, API logs, payment-provider requests/webhooks and background jobs. Never place authorization tokens, ticket-access credentials or payment secrets in logs.
