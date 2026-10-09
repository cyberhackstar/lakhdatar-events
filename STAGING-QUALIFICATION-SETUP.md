# Staging Qualification Setup

This release qualifies only against `https://staging-events.neelastack.com`. Production is explicitly refused by the staging gates.

## Required GitHub Environment

Configure these under **Settings -> Environments -> staging**.

### Browser E2E secrets

- `E2E_ADMIN_BEARER`
- `E2E_STAFF_BEARER`
- `E2E_STAFF_EMAIL`
- `E2E_STAFF_PASSWORD`
- `E2E_EVENT_ID`
- `E2E_TICKET_TYPE_ID`
- `E2E_CHECKOUT_EMAIL`
- `E2E_IDEMPOTENCY_KEY`
- `E2E_TICKET_ID`
- `E2E_TICKET_TOKEN`
- `E2E_QR_TOKEN`
- `E2E_GATE`

`E2E_RUN_MUTATIONS` remains `false` by default. Enable it only when the disposable staging test data is prepared for checkout/check-in mutations.

### Enterprise load-test secrets

- `LOADTEST_EVENT_SLUG`
- `LOADTEST_TICKET_ID`
- `LOADTEST_TICKET_TOKEN`
- `LOADTEST_ADMIN_BEARER`
- `LOADTEST_ADMIN_EVENT_ID`
- `LOADTEST_EVENT_ID`
- `LOADTEST_TICKET_TYPE_ID`
- `LOADTEST_STAFF_BEARER`
- `LOADTEST_GATE`
- `LOADTEST_CHECKIN_QR_TOKENS`
- `LOADTEST_IDEMPOTENCY_KEY`
- `LOADTEST_DATABASE_URL`

## Immutable test images

The workflows have safe pinned defaults so missing image variables do not break qualification:

- PostgreSQL: `postgres:17-alpine@sha256:f02121de6f74d30d8a94cd1d9584125e2178d7e6c377d8130112d4e52d867995`
- Flyway: `flyway/flyway:13.8.0-alpine@sha256:533745f3b566788a3ba7e5bb238a4596cf96a3a4c4531b5efca20962ef5ff3b8`
- k6: `grafana/k6:2.2.0@sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6`
- OWASP ZAP: `zaproxy/zap-stable:2.17.0@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef`

Repository/environment overrides remain supported when explicitly configured.
