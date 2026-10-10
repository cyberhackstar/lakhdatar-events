# Staging Browser E2E Qualification — v2.0.38

The stateful browser E2E suite is restricted to `https://staging-events.neelastack.com`. The Playwright configuration and provisioning helper reject production origins; the provisioning helper also requires the declared environment to be `staging` or `local` and refuses an environment config marked as production.

## One-time GitHub environment setup

Create **one** protected secret named `E2E_STAGING_PROVISIONING_CONFIG` in the GitHub `staging` environment. Do not put `CASHFREE_APP_ID`, `CASHFREE_SECRET_KEY`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, or webhook secrets in this E2E secret; the current provisioner intentionally rejects those keys. This is deliberately separate from the deployment `STAGING_ENV_FILE`: do **not** pass the full deployment file to browser tests, because it may contain database, JWT-signing, SMTP, and other runtime secrets. Keep this E2E-only secret limited to the staging marker, the desired provider name, and the dedicated staging E2E admin identity/MFA seed. Do not duplicate payment gateway API keys in the browser-test secret: the deployed backend is the source of truth and must independently pass an authenticated sandbox-mode preflight before fixture creation.

Example shape (replace every placeholder with the real staging-only value; do not commit this file):

```dotenv
APP_ENV=staging
DEFAULT_PAYMENT_PROVIDER=CASHFREE
E2E_ADMIN_EMAIL=e2e-admin@your-staging-domain.example
E2E_ADMIN_PASSWORD=REPLACE_WITH_DEDICATED_ADMIN_PASSWORD
E2E_ADMIN_TOTP_SECRET=REPLACE_WITH_BASE32_TOTP_SEED
E2E_ORGANIZER_SLUG=your-staging-organizer-slug
```

`DEFAULT_PAYMENT_PROVIDER` selects `CASHFREE` or `RAZORPAY`; it does not carry gateway credentials. Configure the actual staging backend deployment's `STAGING_ENV_FILE` separately: Cashfree must use sandbox credentials and `CASHFREE_BASE_URL=https://sandbox.cashfree.com/pg`; Razorpay must use an `rzp_test_` key ID and the standard Razorpay API base URL. The backend now refuses to boot in staging if any configured provider has an unsafe endpoint/key mode, and the authenticated `/api/v1/admin/qualification/payment-safety` preflight verifies the live staging backend's mode before the E2E provisioner creates any resource. If both providers are configured in staging, both must be safe; the configured default provider must also be present and safe. A staging deployment using a `prod`/`production` Spring profile fails closed. Do not include gateway API keys, production topology values, `DB_PASSWORD`, `JWT_SECRET`, SMTP/mail passwords, or the application's full runtime configuration in the E2E secret.

`E2E_ORGANIZER_SLUG` may be omitted only when staging has exactly one organizer; when there is more than one, it is required to prevent assigning disposable accounts to the wrong organizer.

The admin account must already exist, have access to the selected organizer, and be enrolled in privileged MFA. Its password and original base32 TOTP seed are required when `MFA_REQUIRED_FOR_PRIVILEGED=true`. The automation intentionally does not enroll a pre-existing admin or weaken MFA. Use a dedicated staging-only identity and rotate the password/seed according to your credential policy.

## Browser E2E — no per-test fixture secrets

When `E2E_RUN_MUTATIONS=true`, `e2e/global-setup.js` creates a unique, disposable fixture set through authenticated application APIs: a future-dated event, ticket type, organizer-team scanner, organizer-team event manager, two complimentary tickets, a decoded QR credential, and an idempotency key. Playwright workers use this one coherent generated set. Teardown unassigns the test accounts, deactivates them (including token revocation by the API), and cancels the event. If cleanup fails, qualification fails instead of reporting success. Abrupt cancellation of a workflow or runner cannot guarantee teardown, so periodically review the staging event/team list for orphaned `e2e-*` records.

The former per-test fixture values are generated automatically and no longer need GitHub secrets: `E2E_ADMIN_BEARER`, `E2E_STAFF_BEARER`, `E2E_STAFF_EMAIL`, `E2E_STAFF_PASSWORD`, `E2E_EVENT_ID`, `E2E_TICKET_TYPE_ID`, `E2E_CHECKOUT_EMAIL`, `E2E_IDEMPOTENCY_KEY`, `E2E_TICKET_ID`, `E2E_TICKET_TOKEN`, `E2E_QR_TOKEN`, and `E2E_GATE`.

Staging explicitly rejects any supplied legacy fixture IDs, bearer tokens, ticket credentials or QR tokens rather than mixing stale and generated resources. Remove the old per-test fixture secrets and variables from the staging environment after switching to this release. `E2E_AUTO_PROVISION=false` is a local/debug escape hatch only; a staging mutation run fails if it is set.

## Workflow behavior

- Manual dispatch of `e2e-staging.yml` defaults `run_mutations` to `true`, so it runs the complete browser suite.
- Weekday scheduled runs intentionally execute only the read-only public-security suite.
- The enterprise release qualification browser job runs the self-test and complete stateful browser suite.
- The enterprise **load-test** job is separate and continues to require its own `LOADTEST_*` data/credential values; this change removes manual fixture maintenance for browser E2E, not the load-test contract.

## Local, offline provisioning self-test

From `e2e/`, run:

```sh
npm ci
npm run selftest
```

This runs an in-process mock API on loopback; it does not call staging or production. The self-test covers server payment-mode preflight contracts, provider selection without gateway keys in the E2E secret, runtime-generated MFA, credential sourcing, QR token plumbing, password rotation, verified fixture teardown, retry policy, and cleanup failures. The real staging preflight is backed by server configuration checks, not by values in this secret. For local end-to-end browser tests, set `E2E_ENV=local` and use a `127.0.0.1`/`localhost` URL only.
