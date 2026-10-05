# Enterprise observability

Neelastack Events uses the three observability pillars—metrics, logs and traces—with a separate business-integrity layer. Spring Boot’s supported Micrometer/OpenTelemetry path provides request observations and trace context; Prometheus/Alertmanager provides SLI alerting; Loki/Alloy provides searchable container logs; Tempo provides distributed traces.

## Production architecture

```text
                         +-------------------+
Public HTTPS -> NGINX -> | Spring Boot API   | -> PostgreSQL
                         | metrics + traces  | -> Redis
                         +---------+---------+
                                   | OTLP
                                   v
                                Tempo
                                   ^
Docker stdout -> Alloy -> Loki    | traces-to-logs
                                   |
Prometheus <- exporters ----------+---- Grafana
      |
      +--> Alertmanager --> ops/paging receiver
```

## Required signals

- HTTP request rate and 4xx/5xx ratio, p50/p95/p99 latency.
- PostgreSQL exporter plus Hikari pool saturation and dependency health.
- Redis exporter plus application dependency latency/health.
- JVM heap, GC, live threads and container CPU/memory/restarts.
- Payment pending/stale counts, provider-order recovery, webhook backlog/stuck jobs and refund backlog.
- Reservation cleanup, ticket check-ins, recent order flow and mail failures.
- NGINX active/accepted connections and public synthetic HTTPS availability.

## Business integrity

The application exports low-cardinality business gauges refreshed on a bounded schedule. These include payment reconciliation queues, refund and webhook queues, held/expired reservations, ticket activity, recent orders and publication readiness.

The publish operation is server-authoritative: the UI calls a read-only readiness endpoint for explanation, and the transactional publish command evaluates the same policy immediately before changing state. A stale UI cannot bypass lifecycle invariants.

## Correlation and traces

The edge generates or preserves `X-Correlation-ID`, forwards it to the backend, and the backend places it in MDC. NGINX structured access logs, backend ECS logs, audit records and check-in records therefore share a searchable correlation identifier. Never log bearer tokens, ticket access credentials or provider secrets.

Production uses 10% trace sampling by default. Payment and event APIs inherit the W3C trace context from the incoming request and export spans through OTLP to Tempo. Increase sampling temporarily during incident response rather than running 100% tracing permanently on every request.

## Alerting philosophy

Prefer user-impacting symptoms over noisy implementation alerts. Critical alerts cover public availability, API availability, database/Redis dependency loss, stale payments and stuck payment webhooks. Warning alerts cover sustained latency, pool contention, mail failures, reservation cleanup backlog, JVM pressure, exporter outages and disk headroom.

## Access

Grafana is loopback-only by default. Access it through a private VPN or SSH tunnel. Prometheus, Alertmanager, Loki, Tempo and all exporters remain private Docker-network services.

## Retention

The default configuration is sized for a single VM: 30 days of metrics and 7 days of logs/traces. Tune based on disk capacity and backup requirements. For multi-VM production, move monitoring storage to a durable/centralized backend before treating it as a complete disaster-recovery system.
