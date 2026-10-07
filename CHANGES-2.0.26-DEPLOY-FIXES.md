# v2.0.26 — deployment readiness fixes

- Backend container healthcheck in `infra/docker-compose.prod.yml`, `infra/docker-compose.staging.yml` and `docker-compose.yml` now probes the management port (8081). Actuator is not served on 8080, so the old probe kept the backend unhealthy and blocked `web`/`edge` from starting.
- `.env.example`: documented that SMTP (MAIL_HOST/MAIL_FROM/MAIL_USERNAME/MAIL_PASSWORD) is mandatory in production (enforced by `ProductionConfigurationGuard`); `BACKUP_REMOTE_REQUIRED` now defaults to `false` with instructions (set `true` once `BACKUP_REMOTE_URI` exists); added `OTEL_SDK_ENABLED=false` and `OPS_LOGS_ENABLED=false` for the single-VM stack without Tempo/Loki.
- `.github/workflows/production.yml`: post-deploy smoke strictness comes from repository variable `SMOKE_ENTERPRISE` (default `false`). Set it to `true`, together with secrets `SMOKE_TICKET_ID` and `SMOKE_TICKET_TOKEN`, once a real test ticket exists.
- Added `.gitattributes` forcing LF for shell scripts, Dockerfiles, YAML, nginx and SQL files.
- No application code, migrations or version number changed.
