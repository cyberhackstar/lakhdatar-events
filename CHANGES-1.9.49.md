# v1.9.49 — Enterprise observability + publish reliability

- Added a production observability stack: Prometheus, Alertmanager, Grafana, Loki, Tempo, Alloy, node-exporter, cAdvisor, PostgreSQL exporter, Redis exporter, NGINX exporter and public blackbox probes.
- Added provisioned Grafana SRE, application-performance and infrastructure dashboards.
- Added Spring Boot OpenTelemetry tracing configuration, structured ECS console logs and edge-to-backend correlation propagation.
- Added low-cardinality business metrics for payments, webhooks, refunds, reservations, ticket activity, publication readiness and recent traffic.
- Added production monitoring alert rules and exporter-health safeguards.
- Hardened NGINX with private `stub_status` and exact correlation forwarding.
- Added server-authoritative publication readiness and aligned the transactional publish operation with the same readiness policy.
- Added publication-policy unit tests so the publish gate is continuously validated.
- Improved frontend publication readiness refresh after event/ticket changes and prevented cached readiness responses.
- Made the default payment provider deployment-configurable (CASHFREE by default) so new event workflows do not silently target an unconfigured legacy gateway.
- Added a dedicated Grafana Business & Payments dashboard and a synthetic public API probe for early customer-impact detection.
