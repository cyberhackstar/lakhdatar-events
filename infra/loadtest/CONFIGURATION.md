# Enterprise staging load-test configuration

The enterprise load gate deliberately fails closed if its staging fixture inputs are missing. The
2026-10-10 qualification logs showed every `LOADTEST_*` value blank, so no k6 workload started.
This is an environment configuration issue, not an application runtime failure.

In GitHub repository **Settings → Environments → staging → Environment secrets**, configure the
following names with values from a dedicated, disposable staging load-test fixture:

| GitHub secret | Used for |
|---|---|
| `LOADTEST_EVENT_ID` | Event UUID. The workflow maps this to `EVENT_ID`, `ADMIN_EVENT_ID`, and `LOADTEST_EVENT_ID`. |
| `LOADTEST_TICKET_TYPE_ID` | Ticket-type UUID belonging to that event. |
| `LOADTEST_STAFF_BEARER` | Scanner staff bearer token for that test event. |
| `LOADTEST_CHECKIN_QR_TOKENS` | Comma-separated QR tokens issued for disposable test tickets. |
| `LOADTEST_TICKET_ID` | Ticket UUID for read/PDF qualification. |
| `LOADTEST_TICKET_TOKEN` | Corresponding ticket-access token. |
| `LOADTEST_ADMIN_BEARER` | Admin bearer token authorized to read the fixture event. |
| `LOADTEST_DATABASE_URL` | Restricted connection string to the staging database for invariant checks. |
| `LOADTEST_EVENT_SLUG` | Optional event slug for catalog/SEO-specific checks. |
| `LOADTEST_GATE` | Optional scanner gate name; defaults to `Gate 1`. |
| `LOADTEST_IDEMPOTENCY_KEY` | Optional fixed key for the idempotency scenario; defaults to a generated key. |

Do not use production IDs, production bearer tokens, or a production database URL. Keep this event
separate from customer events and use only sandbox payment configuration. Restrict `LOADTEST_DATABASE_URL`
to the minimum read access required by `infra/loadtest/verify-invariants.sql`.

**Token expiry warning:** `LOADTEST_ADMIN_BEARER` and `LOADTEST_STAFF_BEARER` are access tokens, not
permanent credentials. If the application expires them, manually storing them as environment secrets
will become stale and the workflow will fail later. Refresh them before each qualification run, or
implement an authenticated just-in-time fixture/bootstrap step in a subsequent infrastructure change.
Do not extend JWT lifetimes solely to accommodate load testing.

The gate writes `loadtest-results/enterprise-gate-preflight.txt` containing only a pass/blocked status
and missing variable names. It never writes the secret values to evidence artifacts.
