# Validation — v1.9.49

## Static validation

- Platform baseline: executed after package generation.
- YAML/JSON: parsed and validated with local tooling.
- Docker Compose observability config: cross-compose dependencies removed; Grafana loopback-only.
- NGINX config structure: checked for private `/stub_status` and correlation forwarding.
- Publication policy: unit tests added for ready, missing tickets, inactive tickets, unauthorized role and idempotent published state.

## Monitoring coverage

Prometheus recordings cover throughput, 4xx/5xx, p95/p99 latency and public synthetic success. Grafana dashboards cover SRE, application performance and infrastructure. Loki/Tempo datasources are provisioned with trace-to-log and trace-to-metrics links.

## CI gate

The repository environment used for package preparation does not contain Maven/Node dependency caches sufficient to execute the complete CI suite locally. The authoritative gate remains GitHub Actions `mvn -B -ntp clean verify` plus the frontend production build and test workflow.

## Additional preflight

- Business & Payments Grafana dashboard is provisioned and the public `/api/v1/public/events/featured` endpoint is included in synthetic monitoring.
