# GitHub staging environment setup

Create a GitHub Environment named `staging` and use dedicated credentials for the staging deployment. The exact names below match the repository workflows.

## Deployment secrets

- `STAGING_DEPLOY_HOST` — Oracle VM IP/hostname only (the workflow connects as `ubuntu`; an existing `ubuntu@host` value is also accepted). The same Oracle VM may be used for staging if production remains isolated on loopback 4002 and staging on loopback 4003.
- `STAGING_DEPLOY_SSH_KEY` — dedicated private deploy key with no interactive shell requirement beyond the deployment account.
- `STAGING_DEPLOY_KNOWN_HOSTS` — pinned SSH host key(s).
- `STAGING_ENV_FILE` — complete `.env.staging.example` content with all placeholders replaced.

## Browser E2E secrets

Use the dedicated staging environment identities and records listed in `e2e/README.md`: admin/staff bearer tokens, staff login credentials, disposable event and ticket IDs, ticket access token, QR token and gate. Rotate the stateful checkout/check-in records for each manual certification run.

Set `E2E_BASE_URL` only when overriding the default; the workflow falls back to the public staging variable.

## Load-test secrets

Create the dedicated staging event/ticket records and configure the `LOADTEST_*` secrets required by `.github/workflows/load-test.yml`. `LOADTEST_DATABASE_URL` must point to the staging database only. Never store a production database URI in the staging environment.

## Certification variables

The enterprise release qualification workflow additionally needs reviewed, digest-pinned:

- `POSTGRES_CERTIFICATION_IMAGE`
- `FLYWAY_CERTIFICATION_IMAGE`
- `ZAP_IMAGE`

The `enterprise-certification` environment still requires the independent operational evidence listed in `docs/ENTERPRISE-CERTIFICATION-ENVIRONMENT.md`; these values must never be fabricated merely to make the workflow pass.

## Public hostname

Recommended GitHub environment variable:

```text
STAGING_PUBLIC_URL=https://staging-events.neelastack.com
```

The repository workflows default to this hostname when a specific URL is not supplied.
