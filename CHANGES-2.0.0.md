# Neelastack Events v2.0.0

Release type: enterprise operations-center evolution.

## Added

- Premium, read-only **Platform Operations Center** inside the existing ADMIN console at `/admin/operations`.
- Aggregate 24-hour KPIs for captured payments, refunds, orders, successful/failed payments, tickets issued and accepted check-ins.
- Active/upcoming event posture and worker-tier state.
- PostgreSQL and Redis dependency state with probe latency.
- Payment integrity signals: pending, stale, provider recovery and refund queues.
- Webhook, reservation-cleanup and ticket-mail queue posture.
- Automatic 15-second UI refresh with manual refresh and graceful stale-state handling.
- Backend `/api/v1/admin/ops/dashboard` read-only endpoint protected by `ADMIN` role and `Cache-Control: no-store`.
- Contract tests ensuring the operations dashboard remains admin-only and aggregate-only.

## Security and reliability

- No customer PII, raw webhook payloads, payment secrets, provider credentials or QR credentials are returned by the operations dashboard.
- Existing Prometheus, Grafana, Loki and Tempo monitoring architecture remains the deep-observability plane; v2 does not expose those systems through the public application.
- No payment, ticket issuance, inventory, refund or check-in write path was changed for the UI feature.
- Existing `/admin/ops/health` endpoint remains available for compatibility.
- No database migration is required for the operations-center feature.

## Validation requirement

Run the full project CI before production promotion:

- `mvn -B -ntp clean verify`
- frontend `npm ci`
- frontend production build
- platform baseline verification
- Docker/Compose validation
- staging smoke tests for login, checkout, payment verification/webhooks, ticket issuance, QR check-in, refunds and public SEO routes.
