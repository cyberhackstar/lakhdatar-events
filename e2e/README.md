# Enterprise browser E2E qualification

This is a disposable-staging browser suite. Read-only public/ticket checks run across desktop Chromium, Android Chrome and iOS Safari; stateful checkout/check-in mutation tests run once on Chromium. Automatic provisioning is restricted to `https://staging-events.neelastack.com` for staging or a localhost URL for explicitly local runs. Do not target production.

When `E2E_RUN_MUTATIONS=true`, `global-setup.js` signs in as a staging admin and provisions an event, ticket type, scanner staff member, event manager, complimentary tickets, QR credential, gate and idempotency key using the normal authenticated APIs. These generated values are one coherent set; partial hand-supplied legacy fixture values are rejected. Teardown unassigns and deactivates the generated team accounts and cancels the event, and cleanup failure fails the run.

## Required values for staging automation

Create the protected GitHub `staging` environment secret `E2E_STAGING_PROVISIONING_CONFIG`. It must be a *minimal E2E-only* dotenv configuration with `APP_ENV=staging`, a dedicated staging admin identity, the admin's TOTP seed when privileged MFA is required, and one complete payment sandbox configuration. Do **not** wire the deployment `STAGING_ENV_FILE` into E2E jobs: it may contain DB, JWT-signing, SMTP and other runtime secrets. See `../STAGING-QUALIFICATION-SETUP.md` for its expected shape.

The selected provider must be sandbox-only: Razorpay test keys beginning `rzp_test_` (plus secret and webhook secret), or Cashfree using `https://sandbox.cashfree.com/pg`. The provisioner refuses the Cashfree production endpoint and live/non-test Razorpay key configurations before writing fixture data. Existing admins are never automatically enrolled in MFA and the workflow must not disable MFA to pass tests.

The previous per-test `E2E_*` fixture IDs, bearer tokens, email/password and QR values are produced automatically; they should not be stored in GitHub secrets. If legacy fixture values remain set in a local shell, clear them rather than mixing a stale subset with auto-provisioned resources. `E2E_AUTO_PROVISION=false` bypasses provisioning for controlled debugging only and must not be set in staging workflows.

## Local self-test

```sh
npm ci
npm run selftest
```

This suite uses an in-process mock API and QR generator; it has no database or external network dependency. It checks secret sourcing, provider sandbox guards, MFA challenge refresh/enrollment rules, password rotation, decoded ticket QR content and cleanup handling.

## Local E2E browser tests

Start an isolated local stack reachable at `http://127.0.0.1:4002`. Set `E2E_ENV=local` and `E2E_BASE_URL=http://127.0.0.1:4002`. The public-security browser gate can run without mutation fixtures; full local mutations require local admin credentials and a configured sandbox provider.

### Windows local smoke test

From the repository root:

```powershell
.\infra\local-e2e.ps1
```

This starts the local services and runs public-security browser qualification.
