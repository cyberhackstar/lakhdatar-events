# Production Deployment — v1.9.50

1. Deploy the backend image and frontend/edge images from this release.
2. Run Flyway; `V31__payment_attempt_history.sql` is forward-only and creates payment attempt recovery indexes.
3. Restart the application stack in the normal production order.
4. Start the monitoring stack after `lakhdatar_net` exists.
5. Validate Prometheus `/targets`, `/rules` and remote-write readiness.
6. Validate Grafana dashboards and Tempo traces/service graph.
7. Validate `/actuator/health/readiness` and public HTTPS synthetic probes.
8. Exercise one controlled checkout, webhook, recovery, ticket PDF, CSV and publish flow before declaring release healthy.
9. Do not expose Prometheus/Alertmanager/Loki/Tempo/exporters publicly.
