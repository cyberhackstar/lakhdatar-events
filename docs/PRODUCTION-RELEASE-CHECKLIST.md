# Production Release Checklist

Before the first public deployment:

1. Configure `production` GitHub environment approval rules.
2. Configure `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `TICKET_VIEW_SECRET`, and `QR_SIGNING_SECRET` with unique high-entropy values.
3. During key rotation, keep `TICKET_VIEW_SECRET_PREVIOUS` and `QR_SIGNING_SECRET_PREVIOUS` populated until all prior tokens/QRs are outside the operational grace period.
4. Configure at least one verified payment provider, including its webhook secret.
5. Run CI through backend tests, frontend build/tests, CodeQL, Gitleaks, runtime-dependency npm audit, Trivy, SBOM/provenance, and image attestation/provenance verification.
6. Take and restore-verify the database backup before deployment.
7. Deploy by immutable Git SHA only; never use `latest` in production.
8. Confirm backend readiness, `/edge-health`, SSR home, catalogue API, `robots.txt`, and `sitemap.xml` after deployment.
9. Keep Flyway changes expand/contract-compatible; never assume an application image rollback also rolls back the database schema.
10. Configure off-host encrypted backup retention and perform a clean-VM restore drill before assigning an RPO/RTO.
11. Bring up Prometheus/Alertmanager/Grafana on the private network and configure alerts for payment/reconciliation, refund backlog, checkout latency, check-in latency, 5xx rate, DB pool saturation, Redis health/memory, disk usage and container restarts.
