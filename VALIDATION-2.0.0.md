# Validation — v2.0.0

## Scope

v2.0.0 adds the read-only Platform Operations Center on top of the v1.9.56 production baseline.

### Static checks performed

- Backend controller/service source reviewed.
- Operations endpoint is protected by `ADMIN` role.
- Operations dashboard responses are explicitly `no-store`.
- Dashboard queries are aggregate-only.
- No customer email, phone, attendee name, raw webhook payload, provider signature or credential field is exposed by the dashboard DTO/query layer.
- No Flyway migration is introduced for this feature.
- Existing `/admin/ops/health` endpoint is retained.
- Existing monitoring stack remains private; the web console does not proxy or publish Prometheus, Grafana, Loki or Tempo.
- Frontend route remains behind the existing ADMIN guard.
- UI uses a bounded 15-second refresh cadence and does not perform write operations.

### Required CI qualification before deployment

This environment does not have the project's Maven dependencies/node_modules installed, so this package must not be described as fully CI-verified from this workspace alone.

Run in the repository CI runner:

1. `mvn -B -ntp clean verify`
2. `npm ci` in `frontend/`
3. `npm run build` in `frontend/`
4. platform baseline verification
5. Docker Compose/config validation
6. staging smoke tests for authentication, checkout, payment verification, provider webhooks, ticket issuance, QR check-in, refunds and public SEO routes
7. production observability smoke test and alert delivery test
