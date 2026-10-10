# Enterprise staging load-test configuration (automatic fixtures)

The load-test workflow uses the existing protected GitHub Actions `staging` environment and the same
`E2E_STAGING_PROVISIONING_CONFIG` identity-only secret as browser E2E. For stateful tests and the
enterprise gate, it automatically provisions a uniquely named disposable event, ticket type,
scanner staff identity, tickets, QR credential and idempotency key through the authenticated staging
API. It maps the generated values directly to k6 in process, runs the requested scenarios, verifies
post-run database invariants over the existing pinned staging SSH connection (even when a k6 threshold
fails, before cleanup), and cancels the event / deactivates the generated team accounts in a `finally` cleanup path.

**Do not create or maintain `LOADTEST_EVENT_ID`, `LOADTEST_TICKET_ID`, `LOADTEST_TICKET_TOKEN`,
`LOADTEST_ADMIN_BEARER`, `LOADTEST_STAFF_BEARER`, `LOADTEST_CHECKIN_QR_TOKENS`, or
`LOADTEST_DATABASE_URL` secrets.** They are no longer used. The only app identity/config secret needed
for stateful qualification is `E2E_STAGING_PROVISIONING_CONFIG`, which should contain only:

```dotenv
APP_ENV=staging
DEFAULT_PAYMENT_PROVIDER=CASHFREE
E2E_ADMIN_EMAIL=...
E2E_ADMIN_PASSWORD=...
E2E_ADMIN_TOTP_SECRET=...
E2E_ORGANIZER_SLUG=lakhdatar-events
```

Choose `RAZORPAY` only when the staging provider-safety endpoint reports Razorpay test mode. Payment
gateway API secrets remain only in the protected staging deployment environment and are never read by
the workflow. The provisioner fails closed unless the authenticated staging API confirms sandbox/test
mode. The load gate uses the existing `STAGING_DEPLOY_HOST`, `STAGING_DEPLOY_SSH_KEY`, and
`STAGING_DEPLOY_KNOWN_HOSTS` secrets only to run a read-only invariant query inside the isolated staging
PostgreSQL container. It does not download, print, or source `STAGING_ENV_FILE`, and no database URL or
DB password is sent to the runner.

The load-test fixture uses separate `loadtest-<run-id>` slugs and up to 50,000 disposable ticket units
to avoid exercising a low inventory cap in place of concurrency. It is not a customer event; it is
cancelled at the end of the run. The complimentary QR credential is deliberately reused to test
accepted/duplicate scan behavior. Generated access tokens are masked in GitHub Actions, are only passed to the specific k6 container variables that use them, and are not written to artifacts or GitHub step outputs. The provisioning secret and SSH key are removed from the k6 subprocess environment; the SSH key is passed only to the separate invariant-verification process.

Tuning values (`LOADTEST_THOUSANDS_MAX_VUS`, `LOADTEST_THOUSANDS_RAMP`,
`LOADTEST_THOUSANDS_HOLD`, and `LOADTEST_CHECKOUT_TARGET_RATE`) are optional GitHub environment variables
with safe defaults; they are not credentials. Non-mutating `catalog.js`, `burst.js` (against the featured endpoint when no slug is supplied), `seo.js`
and `thousands.js` scenarios can still run without provisioning. Stateful scenarios provision fixtures
automatically.
