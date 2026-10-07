# v2.0.26 — isolated staging environment

This patch adds a first-class disposable staging deployment without changing the production service names, volumes, ports or public hostname.

## Added

- `infra/docker-compose.staging.yml` — isolated PostgreSQL, Redis, backend, SSR web and edge stack.
- `infra/deploy/deploy-staging.sh` — immutable SHA deployment with staging-only safety checks and readiness gates.
- `infra/smoke/staging-smoke.sh` — HTTPS-header, public-route, actuator-isolation and staging noindex smoke checks.
- `infra/cloudflare/config.staging.example.yml` — `staging-events.neelastack.com -> http://localhost:4003` tunnel target.
- `.env.staging.example` — staging-only environment template.
- `.github/workflows/staging.yml` — signed-image staging deployment workflow.
- `docs/STAGING-ENVIRONMENT.md` and `docs/STAGING-RELEASE-FLOW.md` — setup and release procedure.

## Safety corrections

- Production remains `events.neelastack.com` on loopback `4002` with the original production Compose project and volumes.
- Staging uses separate Compose project/network/container names and PostgreSQL/Redis volumes.
- Staging deployment refuses the production public origin and the enterprise production topology flag.
- Staging Cashfree checkout cannot be configured with the production Cashfree API endpoint; Razorpay staging accepts only `rzp_test_` keys.
- Staging responses carry `X-Robots-Tag: noindex, nofollow` so qualification traffic is not indexed.
- Scheduled browser E2E runs only non-mutating coverage; manual enterprise qualification explicitly enables stateful checkout/check-in tests.

## Release consistency

- Corrected stale `2.0.24` application/telemetry defaults in the production and HA reference configurations to `2.0.26`.
