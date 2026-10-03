# Deployment: Oracle VM

Target directory: `/home/ubuntu/apps/lakhdatar-events`. Public host port: `4002` (loopback). Public URL: https://events.neelastack.com.

```text
/home/ubuntu/apps/
  neelastack/          # existing app, port 4000, untouched
  gmail/               # existing app, port 4001, untouched
  lakhdatar-events/    # this platform, port 4002
```

## One-time setup

1. `mkdir -p /home/ubuntu/apps && cd /home/ubuntu/apps`
2. Clone the repository into `/home/ubuntu/apps/lakhdatar-events` and verify the VM can `git fetch origin main`.
3. `cd /home/ubuntu/apps/lakhdatar-events`
4. Create `.env` from `.env.example` (chmod 600). Set strong `DB_PASSWORD`, `JWT_SECRET`, `TICKET_VIEW_SECRET`, `QR_SIGNING_SECRET`, at least one complete payment-provider credential set, `CORS_ALLOWED_ORIGINS=https://events.neelastack.com`, and `PUBLIC_BASE_URL=https://events.neelastack.com`. If using Cashfree, set its complete `CASHFREE_*` credential set; if using Cloudinary, set its complete `CLOUDINARY_*` credential set.
5. Add the Cloudflare ingress rule (see `infra/cloudflare/config.example.yml`): `events.neelastack.com -> http://localhost:4002`. Keep the existing rules for the other apps.
6. Fresh production setup: keep `BOOTSTRAP_ENABLED=false`. Temporarily set `INITIAL_ADMIN_SETUP_ENABLED=true` and a unique `INITIAL_ADMIN_SETUP_TOKEN` (32+ random bytes), deploy, then open `https://events.neelastack.com/setup/initial-admin` and create the first administrator/organizer. After successful creation, set `INITIAL_ADMIN_SETUP_ENABLED=false` and redeploy. No sample event is created by this flow.
7. Optional: `infra/backup/install-cron.sh` for scheduled database backups.

## Releases

GitHub Actions uses two workflows only: `CI` and `Production`. `CI` builds/tests the backend, builds/validates the SSR frontend (including an SSR server smoke test), runs security/dependency scans and the weekly security sweep, and pushes three ARM64 images tagged with the full Git SHA: `lakhdatar-backend`, `lakhdatar-web`, `lakhdatar-edge`. `Production` then follows the same deployment model used by the Neelastack production project: the VM repository is fast-forwarded/reset to the exact release SHA and `infra/deploy/deploy.sh <sha>` is executed in place, which:

1. validates the Compose model, 2. takes a database backup, 3. pulls the immutable images, 4. starts PostgreSQL/Redis, 5. starts the backend (Flyway applies migrations) and waits for the Spring readiness group (`/actuator/health/readiness`) for up to 300 seconds,
6. starts web and edge and waits for the local edge health endpoint, 7. smoke-tests `/`, `/api/v1/public/events/upcoming`, `/robots.txt`, `/sitemap.xml` on `127.0.0.1:4002`.

Backend readiness failures after startup are diagnosed in-place: the deploy script records Compose status, container health state, and the last 250 backend log lines. The backend, production Compose healthcheck, and deploy gate all use the same readiness contract.

Failures before Flyway-backed application startup can roll back automatically. Once Flyway has run, the release is not automatically downgraded because database schema changes are forward-only; the deployment is marked failed and the operator must use a forward fix or a verified schema-compatible rollback.
Manual rollback: run the `Production` workflow with operation `rollback`, or run `./infra/deploy/rollback.sh` on the VM after confirming the previous release is schema-compatible. Manual deploy accepts an explicit 40-character release SHA; it must already have passed CI and have corresponding GHCR images. Application rollback is intentionally not automatic after Flyway has run.

Repository secrets: `DEPLOY_HOST`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`. `DEPLOY_HOST` includes the SSH user, for example `ubuntu@203.0.113.10`. No separate GHCR token secret is required by the workflow. The workflow uses the short-lived GitHub Actions `GITHUB_TOKEN` to authenticate the VM to GHCR for the deployment, then logs out after the operation. No separate GHCR PAT secret is stored in GitHub.

## Upgrading from 1.0.x

Migration `V10__multi_event_catalog.sql` is additive: new nullable/defaulted columns on `organizers`, `venues`, `events`, a widened event status check constraint and new indexes.
Existing event, order, payment, ticket and check-in rows are not modified. The pre-deploy backup is your restore point.
Rolling back the application to 1.0.x keeps working with the V10 schema as long as no event has been moved to `COMPLETED` or `ARCHIVED` (1.0.x does not know those statuses).

## Forwarded headers

The edge maps `X-Forwarded-Proto` from Cloudflare, forwards `Host`, `X-Forwarded-Host` and `CF-Connecting-IP`. Canonical, OpenGraph and sitemap URLs use the configured
public origin (`PUBLIC_BASE_URL` / `environment.prod.ts`), never the request Host, so `localhost` or internal container names can never appear in customer-facing URLs.

## Verification

Backend readiness inside the container should return `status=UP` before the web and edge services are started. The production deploy gate defaults to 300 seconds for backend readiness and 180 seconds for edge readiness. These bounds are configurable with positive-integer environment variables.

```bash
curl -fsS http://127.0.0.1:4002/edge-health
curl -fsS http://127.0.0.1:4002/api/v1/public/events/upcoming
curl -fsS https://events.neelastack.com/sitemap.xml
```

## Production deployment smoke contract

The local edge smoke tests use the canonical public Host and HTTPS forwarded-protocol headers. Do not replace them with `Host: 127.0.0.1`; Angular SSR host validation can correctly reject that synthetic production-inaccurate request with HTTP 400.
