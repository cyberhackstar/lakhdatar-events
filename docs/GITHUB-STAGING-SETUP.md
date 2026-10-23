# GitHub staging environment setup

Create a GitHub Environment named `staging` and use dedicated credentials for the staging deployment. The exact names below match the repository workflows.

## Deployment secrets

- `STAGING_DEPLOY_HOST` — Oracle VM IP/hostname only (the workflow connects as `ubuntu`; an existing `ubuntu@host` value is also accepted). The same Oracle VM may be used for staging if production remains isolated on loopback 4002 and staging on loopback 4003.
- `STAGING_DEPLOY_SSH_KEY` — dedicated private deploy key with no interactive shell requirement beyond the deployment account.
- `STAGING_DEPLOY_KNOWN_HOSTS` — pinned SSH host key(s).
- `STAGING_ENV_FILE` — complete `.env.staging.example` content with all placeholders replaced.

## Browser E2E secret

Create one dedicated protected secret named `E2E_STAGING_PROVISIONING_CONFIG`. Follow `STAGING-QUALIFICATION-SETUP.md` for its exact stage-only fields. The global fixture provisioner generates disposable event/team/ticket/QR/checkout fixtures automatically. Do **not** configure legacy `E2E_ADMIN_BEARER`, `E2E_STAFF_BEARER`, event IDs, ticket IDs, QR tokens, or ticket credentials; do not put the deployment `.env` or payment-provider credentials in the E2E secret. A pre-existing dedicated staging admin identity with MFA is required; the automation does not enroll MFA or weaken it.

`E2E_BASE_URL` / `STAGING_PUBLIC_URL` are optional overrides; workflows default to `https://staging-events.neelastack.com`.

## Load-test configuration

The qualification and manual load-test workflows default to their bounded load profile, and the enterprise gate disables production checkout load. `LOADTEST_*` values are optional GitHub Variables for deliberate profile overrides, not required secrets in the normal path. The automation obtains current staging fixture data from the provisioner and rejects production targets. Do not create a production database URI for staging load tests.

## Certification variables

The enterprise release qualification workflow pins reviewed image digests in its workflow defaults for PostgreSQL, Flyway, k6 and ZAP. The corresponding `POSTGRES_CERTIFICATION_IMAGE`, `FLYWAY_CERTIFICATION_IMAGE`, `K6_IMAGE` and `ZAP_IMAGE` variables are optional overrides; do not create them unless intentionally changing the reviewed image reference.

Release qualification (build, migration, browser, load and DAST) is intentionally independent from operational readiness. For the current one-VM deployment, configure and complete the evidence listed in `docs/SINGLE-NODE-PRODUCTION-READINESS.md`, then run the protected **Single-Node Production Readiness** workflow. It does not certify HA. The separate **Enterprise Production Certification** workflow and `enterprise-certification` environment are for a real multi-node HA topology only. Never fabricate evidence or mark an unperformed drill PASS. After review, the production environment variable `SINGLE_NODE_CERTIFIED_SHA` must equal the exact SHA approved for single-node promotion.

## Public hostname

Recommended GitHub environment variable:

```text
STAGING_PUBLIC_URL=https://staging-events.neelastack.com
```

The repository workflows default to this hostname when a specific URL is not supplied.
