# Neelastack Events — Enterprise Observability

This stack provides the production monitoring plane for the application: Prometheus metrics, Alertmanager routing, Grafana dashboards, Loki logs, Tempo traces, Grafana Alloy Docker log collection, NGINX metrics, host/container telemetry, PostgreSQL metrics, Redis metrics and public synthetic probes.

## What is covered

**Application:** HTTP request rate, 4xx/5xx ratio, p95/p99 latency, JVM heap/GC/threads, Hikari pool saturation, Spring health, structured ECS logs and distributed traces.

**Payments:** pending/stale payments, provider-order recovery, webhook backlog/stuck jobs, refund queue, payment-provider readiness and business reconciliation signals.

**Event operations:** publishable event count, ticket inventory, reservations, check-ins, recent orders, mail delivery failures and worker state.

**Infrastructure:** NGINX edge connections, VM CPU/memory/disk, container CPU/memory/restarts, PostgreSQL sessions/transactions and Redis memory/command volume.

**External availability:** HTTPS probes for the public site, robots.txt and sitemap.xml.

## Security model

Monitoring services share the private `lakhdatar_net` Docker network. Only Grafana is bound to the VM loopback interface (`127.0.0.1:${GRAFANA_PORT:-3100}`); exporters, Prometheus, Alertmanager, Loki and Tempo are not publicly exposed. The NGINX `/stub_status` endpoint is private-network-only.

Use a dedicated PostgreSQL monitoring account with the `pg_monitor` role. Do not reuse the application database password for production monitoring. Redis exporter credentials must be supplied through environment/secrets.

## Start on the production VM

1. Ensure the application stack has created `lakhdatar_net`.
2. Copy `monitoring.env.example` to a protected operator env file, set strong Grafana/Postgres/Redis credentials, and chmod 600 it.
3. Start the observability stack:

```bash
docker compose --env-file infra/monitoring/monitoring.env -f infra/monitoring/docker-compose.observability.yml config
docker compose --env-file infra/monitoring/monitoring.env -f infra/monitoring/docker-compose.observability.yml up -d
```

4. Open Grafana through an SSH tunnel rather than publishing it to the internet:

```bash
ssh -L 3100:127.0.0.1:3100 ubuntu@YOUR_VM
```

Then browse to `http://127.0.0.1:3100`.

## Postgres monitoring role

Use `postgres-monitor-role.sql` as a template. Replace the placeholder password out-of-band; never commit the real secret.

## Incident routing

Alertmanager currently stores/routes alerts to the local `ops-console` receiver without assuming a third-party paging service. Before relying on this for 24x7 incidents, configure an authenticated email, Slack-compatible webhook, PagerDuty or equivalent receiver in `alertmanager.yml` and validate a real alert end-to-end.

## Retention

Prometheus: 30 days by default. Loki and Tempo: 7 days by default. Change retention only after checking VM disk headroom and backup strategy.

## Useful checks

```bash
docker compose --env-file infra/monitoring/monitoring.env -f infra/monitoring/docker-compose.observability.yml ps
docker logs --tail 200 lakhdatar-observability-prometheus-1
curl -fsS http://127.0.0.1:3100/api/health
```

Container names vary if Compose project naming is changed; prefer `docker compose ps` for exact names.
