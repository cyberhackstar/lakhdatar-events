# Production observability

The application exposes Micrometer/Prometheus metrics and the production edge emits structured request telemetry. Start this stack only on the private Docker network (`lakhdatar_net`) and publish Grafana through an authenticated administrative path (for example an SSH tunnel or VPN), never directly to the internet.

Required production operations:

1. `docker compose -f infra/docker-compose.prod.yml up -d`
2. `docker compose -f infra/monitoring/docker-compose.observability.yml up -d`
3. Configure an Alertmanager receiver before relying on critical alerts.
4. Keep Grafana credentials outside Git.
5. Test that a deliberately stopped backend triggers `LakhdatarBackendDown` before production launch.
