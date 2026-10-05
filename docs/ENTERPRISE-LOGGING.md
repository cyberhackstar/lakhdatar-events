# Neelastack Events — Enterprise Logging

## Purpose

The backend emits structured ECS-style operational events and the edge already emits request logs with correlation IDs. v2.0.8 includes a secure, read-only Production Monitor log viewer backed by Loki so an administrator can diagnose failures without shell access.

## Event taxonomy

| Category | Representative events | Useful fields |
|---|---|---|
| HTTP | `http.request.completed`, `api.rejected`, `api.error` | method, route, status, duration, outcome, correlation ID |
| Authentication | `auth.login.*`, `auth.refresh.*`, `auth.password.changed`, `auth.logout.succeeded` | user ID, role, error code, security reason |
| Payment | `order.checkout.initialized`, `payment.verification.*`, `payment.provider.http.*`, `refund.*` | order/payment IDs, provider, status, amount in minor units, duration |
| Inventory | `inventory.reservation.*` | reservation/order IDs, quantity, status |
| Check-in | `checkin.*` | event/ticket IDs, gate, staff ID, result, duration |
| Webhook | `webhook.*` | provider, event ID/type, status, duration, error type |
| Email | `mail.*` | order/job IDs, attempt counts, result, error type |
| Media | `media.*` | purpose, asset ID, size, outcome, error type |
| Recovery jobs | `*.batch.*`, recovery/reset/deferred events | batch size, failure count, duration |

## Privacy and security rules

Logs must never contain passwords, JWT/refresh tokens, authorization headers, cookies, provider signatures, QR credentials, full request/response bodies, or customer contact information. `EnterpriseLog` defensively redacts fields whose names contain secret-bearing terms and truncates long string values.

Payment-provider logging records endpoint path, method, HTTP status, outcome and duration, but never provider credentials or payload bodies.

## Correlation

`X-Correlation-ID` is accepted only when it matches the safe `[A-Za-z0-9._-]{8,80}` format; otherwise the backend generates a UUID. The value is returned to the caller and placed in the MDC so edge/backend failures can be joined in Loki/Grafana.

## Production Monitor

`GET /api/v1/admin/ops/logs` is ADMIN-only and sends `Cache-Control: no-store`. The server bounds the query window to 24 hours and the result size to 200 entries. The client polls every 5 seconds only while the log panel is open and supports service, level, time-window and text filtering.

Loki remains on the private `lakhdatar_net` network. Prometheus, Grafana, Loki and Tempo must not be published directly to the public internet.

## Production diagnosis workflow

1. Start with the Production Monitor platform state and queue posture.
2. Open Live logs and filter for `ERROR`/`WARN` in the affected service.
3. Copy the correlation ID from the failing request.
4. Search the same correlation ID in Loki/Grafana when deeper trace context is required.
5. For payment issues, reconcile against the stored provider payment/refund ID; never diagnose from browser totals alone.
6. Treat stale payment, webhook and refund queues as recovery work; do not manually delete records.

## Retention

Loki retention is controlled by the observability deployment. Keep the retention window long enough to investigate payment/event incidents but short enough to respect the platform's operational data-minimization policy.
