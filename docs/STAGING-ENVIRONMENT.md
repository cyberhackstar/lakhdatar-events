# Neelastack Events — isolated staging environment

`staging-events.neelastack.com` is the disposable HTTPS origin for browser E2E, k6 load testing, DAST and enterprise release qualification. It must never share the production PostgreSQL database, Redis data, secrets or live payment credentials.

## Same-VM layout

A single Oracle VM may host both environments as separate Docker Compose projects:

- Production edge: `127.0.0.1:4002` -> `events.neelastack.com`
- Staging edge: `127.0.0.1:4003` -> `staging-events.neelastack.com`
- Production volumes: `lakhdatar_pg_data`, `lakhdatar_redis_data`
- Staging volumes: `lakhdatar_pg_data_staging`, `lakhdatar_redis_data_staging`
- Production root: `/home/ubuntu/apps/lakhdatar-events`
- Staging root: `/home/ubuntu/apps/lakhdatar-events-staging`

A separate VM is preferable for failure-domain isolation, but the same-VM staging profile remains isolated at the application, network, container-name and data-volume level.

## Cloudflare

Merge `infra/cloudflare/config.staging.example.yml` into the tunnel that runs on the staging VM/host:

```yaml
ingress:
  - hostname: staging-events.neelastack.com
    service: http://localhost:4003
```

Then create/confirm the DNS route:

```bash
cloudflared tunnel route dns <YOUR_TUNNEL_NAME> staging-events.neelastack.com
```

Do not replace the existing production route.

## GitHub staging environment

Create a protected GitHub Environment named `staging` and configure:

- `STAGING_DEPLOY_HOST` — Oracle VM IP/hostname, e.g. `<staging-vm>` (the workflow connects as `ubuntu`)
- `STAGING_DEPLOY_SSH_KEY` — dedicated deploy key
- `STAGING_DEPLOY_KNOWN_HOSTS` — pinned host key entry
- `STAGING_ENV_FILE` — complete staging dotenv content based on `.env.staging.example`

Use sandbox/test payment credentials only. For Razorpay, use a `rzp_test_` key. For Cashfree, use the provider's sandbox/test endpoint and credentials.

The staging secret must contain:

```text
APP_ENV=staging
PUBLIC_HOST=staging-events.neelastack.com
PUBLIC_BASE_URL=https://staging-events.neelastack.com
CORS_ALLOWED_ORIGINS=https://staging-events.neelastack.com
NG_ALLOWED_HOSTS=staging-events.neelastack.com
```

Never include `https://events.neelastack.com` in the staging dotenv file.

## First staging deployment

Run GitHub Actions -> `Staging Deploy` and supply the full 40-character SHA of the green CI commit.

The workflow verifies the signed GHCR images, writes the staging dotenv file to the isolated staging root, pulls the immutable release images and runs `infra/deploy/deploy-staging.sh`.

After the staging origin is healthy, run:

1. `enterprise-e2e-staging` — scheduled runs are read-only; manual runs may enable stateful mutations.
2. `Staging Load Test` — use only disposable event/ticket data and sandbox payment credentials for checkout tests.
3. `Enterprise Release Qualification` — keep `staging_url` at `https://staging-events.neelastack.com` unless a separately approved staging hostname is used.

## E2E test data

The complete browser qualification needs dedicated staging identities and records (`E2E_ADMIN_BEARER`, `E2E_STAFF_BEARER`, `E2E_EVENT_ID`, `E2E_TICKET_TYPE_ID`, `E2E_TICKET_ID`, `E2E_TICKET_TOKEN`, `E2E_QR_TOKEN`, etc.). Stateful checkout/check-in tests are deliberately not run on the scheduled browser workflow because a used QR credential is not reusable. Rotate disposable mutation data for manual certification runs.

## Operational boundary

Staging is not enterprise HA certification. It is the isolated application qualification environment. Production HA still requires two failure-domain nodes, managed/HA database and Redis, off-host PITR, payment-chaos evidence, alerting and protected certification evidence.
