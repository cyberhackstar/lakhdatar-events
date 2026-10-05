# Production deployment — v1.9.49

1. Deploy the v1.9.49 backend/web/edge images with the normal release procedure.
2. Ensure the production `lakhdatar_net` network exists before starting observability.
3. Set `DEFAULT_PAYMENT_PROVIDER=CASHFREE` (or another intentionally configured provider) in the application env; new events inherit this default when no provider is explicitly supplied. Create `infra/monitoring/monitoring.env` from the supplied example and protect it with mode 600.
4. Create the dedicated `lakhdatar_monitor` PostgreSQL role using `postgres-monitor-role.sql`; never reuse or commit the application password.
5. Validate the observability Compose file with `docker compose ... config`, then start it.
6. Open Grafana via SSH tunnel and confirm the three provisioned dashboards.
7. Confirm Prometheus targets are UP for backend, NGINX exporter, host, containers, PostgreSQL exporter, Redis exporter and blackbox exporter.
8. Log in as an admin and open a DRAFT event. Confirm the Publish readiness card explains any missing prerequisite. Add at least one ACTIVE ticket type and a configured payment provider, save, then publish.
9. Confirm the event becomes PUBLISHED and the public page becomes available without a manual database change.
10. Purge Cloudflare cache after releasing the frontend image.
