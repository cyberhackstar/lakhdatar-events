# Deployment: Oracle VM

Target directory: `/home/ubuntu/apps/lakhdatar-events`. Public host port: `4002` (loopback). Public URL: https://events.neelastack.com.

```text
/home/ubuntu/apps/
  neelastack/          # existing app, port 4000, untouched
  gmail/               # existing app, port 4001, untouched
  lakhdatar-events/    # this platform, port 4002
```

## One-time setup

1. `mkdir -p /home/ubuntu/apps/lakhdatar-events && cd /home/ubuntu/apps/lakhdatar-events`
2. Create `.env` from `.env.example` (chmod 600). Set strong `DB_PASSWORD`, `JWT_SECRET`, `TICKET_VIEW_SECRET`, `QR_SIGNING_SECRET`, Razorpay keys, `CORS_ALLOWED_ORIGINS=https://events.neelastack.com`, `PUBLIC_BASE_URL=https://events.neelastack.com`.
3. Add the Cloudflare ingress rule (see `infra/cloudflare/config.example.yml`): `events.neelastack.com -> http://localhost:4002`. Keep the existing rules for the other apps.
4. Optional first-run seed: `BOOTSTRAP_ENABLED=true` with admin credentials, deploy once, then set it back to `false`.
5. Optional: `infra/backup/install-cron.sh` for scheduled database backups.

## Releases

GitHub Actions `CI` builds and tests the backend, builds and validates the SSR frontend, and pushes three ARM64 images tagged with the full Git SHA:
`lakhdatar-backend`, `lakhdatar-web`, `lakhdatar-edge`. `Deploy Oracle VM` then copies `infra/` to the VM and runs `infra/deploy/deploy.sh <sha>`, which:

1. validates the Compose model, 2. takes a database backup, 3. pulls the immutable images, 4. starts PostgreSQL/Redis, 5. starts the backend (Flyway applies migrations) and waits for health,
6. starts web and edge, 7. smoke-tests `/`, `/api/v1/public/events/upcoming`, `/robots.txt`, `/sitemap.xml` on `127.0.0.1:4002`.

Any failure triggers an automatic rollback to the previous tag. The workflow then checks the public URL through Cloudflare and rolls back if it fails.
Manual rollback: run the `Rollback Oracle VM` workflow, or `./infra/deploy/rollback.sh` on the VM.

Repository secrets: `ORACLE_SSH_KEY`, `ORACLE_SSH_HOST`, `ORACLE_SSH_USER`, `ORACLE_GHCR_TOKEN`. `ORACLE_APP_DIR` is no longer used.

## Upgrading from 1.0.x

Migration `V10__multi_event_catalog.sql` is additive: new nullable/defaulted columns on `organizers`, `venues`, `events`, a widened event status check constraint and new indexes.
Existing event, order, payment, ticket and check-in rows are not modified. The pre-deploy backup is your restore point.
Rolling back the application to 1.0.x keeps working with the V10 schema as long as no event has been moved to `COMPLETED` or `ARCHIVED` (1.0.x does not know those statuses).

## Forwarded headers

The edge maps `X-Forwarded-Proto` from Cloudflare, forwards `Host`, `X-Forwarded-Host` and `CF-Connecting-IP`. Canonical, OpenGraph and sitemap URLs use the configured
public origin (`PUBLIC_BASE_URL` / `environment.prod.ts`), never the request Host, so `localhost` or internal container names can never appear in customer-facing URLs.

## Verification

```bash
curl -fsS http://127.0.0.1:4002/edge-health
curl -fsS http://127.0.0.1:4002/api/v1/public/events/upcoming
curl -fsS https://events.neelastack.com/sitemap.xml
```
