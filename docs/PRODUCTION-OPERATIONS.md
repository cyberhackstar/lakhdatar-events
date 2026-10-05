# Production Operations Baseline

## Release flow
- Merge only tested changes to `main`.
- Require the GitHub `production` environment approval before deploy/rollback.
- Deploy immutable Git SHA images; do not use `latest` in production.
- Flyway migrations are forward-only. Use expand/contract migrations so a previous application image remains schema-compatible during rollback.

## Secrets
Required production secrets/environment values include `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `TICKET_VIEW_SECRET`, `QR_SIGNING_SECRET`, payment-provider credentials, and Cloudflare/public-origin values. Previous QR/ticket-view secrets may be supplied during a rotation grace window.

## Backup and DR
- Run `infra/backup/backup-postgres.sh` on a schedule.
- Copy dumps off-host and encrypt them at rest.
- Run `infra/backup/verify-latest-backup.sh` after backup jobs.
- Perform a clean-VM restore drill before declaring an RPO/RTO.

## Observability
- Start `infra/monitoring/docker-compose.observability.yml` on the private application network.
- Configure Alertmanager before relying on critical alerts.
- Monitor payment success/reconciliation, refund backlog, checkout latency, check-in latency, HTTP 5xx rate, database pool saturation, Redis memory, container restarts, disk usage, and Cloudflare tunnel health.

## Scale
The current stack remains a single-node baseline. For multi-instance HA, move PostgreSQL/Redis to managed or clustered services, keep checkout/session/rate-limit state distributed, and place multiple backend/web replicas behind an HA ingress.


## Dedicated worker tier

Set `WORKER_ENABLED=false` on horizontally scaled HTTP API nodes and run one or more replicas of the same backend image with `SPRING_MAIN_WEB_APPLICATION_TYPE=none` and `WORKER_ENABLED=true`. Worker jobs use the Redis distributed lock and are safe to run with multiple replicas. Keep provider, SMTP and application secrets available to the worker tier because reconciliation/refund/mail jobs require them.

## Operations console

Platform administrators use `/admin/operations` for business/event operations. Deep PostgreSQL/Redis health, recovery queues and searchable logs are served by the dedicated `https://monitor.neelastack.com/` SRE console. Treat application dashboards as operator signals, not a replacement for Prometheus/Alertmanager.
