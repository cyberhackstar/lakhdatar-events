# Environment linkage audit (v1.9.22)

Method: every `${VAR}` in `backend/src/main/resources/application.yml` was compared with (a) `.env.example`,
(b) the variables `infra/docker-compose.prod.yml` actually passes into the backend container, and (c) the dev `docker-compose.yml`.
A variable that is in `.env.example` but not passed by Compose is silently ignored on the VM, so editing it does nothing.

## Findings in v1.9.21 (all fixed in v1.9.22)

| Variable | Problem | Fix |
|---|---|---|
| `JWT_ACCESS_TOKEN` | documented, never passed to the container | passed (default `15m`) |
| `TICKET_VIEW_TTL` | documented, never passed | passed (default `24h`) |
| `CHECKIN_EARLY_WINDOW` | documented, never passed | passed (default `30m`) |
| `RAZORPAY_BASE_URL` | documented, never passed (production guard still checks it) | passed (default live URL) |
| `PAYMENT_RECONCILIATION_AGE_MS`, `PAYMENT_RECONCILIATION_SWEEP` | documented, never passed | passed |
| `DEFAULT_ORGANIZER_LOGO_URL` | pointed every organizer at Lakhdatar's logo | removed; organizer logos come from Cloudinary uploads |
| `NEELASTACK_LOGO_URL` | env override of platform mark | removed; fixed to `/assets/neelastack-logo.png` |
| dev compose | no `INITIAL_ADMIN_*`, `CLOUDINARY_*`, `CASHFREE_*`: setup page and uploads could not be tried locally | added |

The values that were being ignored equal the code defaults, so production behaviour did not change; the fix makes future edits effective.

## Variables with no `.env.example` entry (defaults apply; add to `.env` + Compose only if you need to change them)

`NEELASTACK_NAME`, `NEELASTACK_PROMO_*`, `CHECKOUT_SESSION_TTL`, `MAX_TICKETS_PER_ORDER`, `RESERVATION_HOLD/SWEEP`, `REFUND_RECOVERY_SWEEP`,
`PAYMENT_ORDER_RECOVERY_SWEEP`, `*_RATE_LIMIT_*`, `RATE_LIMIT_FAIL_CLOSED` (must stay true in prod), `DB_POOL_*`, `QR_IMAGE_SIZE`,
`RAZORPAY_*_TIMEOUT_MS`. Not wired through Compose, so they cannot be changed on the VM without editing Compose.

Dead config: `SUPPORT_EMAIL` / `SUPPORT_PHONE` exist in `application.yml` (placeholder `support@example.com`) but no class reads them.

## "Real use" checklist (values only you can confirm; wiring is now correct)

| Item | Must be |
|---|---|
| `PUBLIC_BASE_URL`, `PUBLIC_HOST`, `NG_ALLOWED_HOSTS`, `CORS_ALLOWED_ORIGINS` | the real public host `events.neelastack.com` (https for URLs) |
| `frontend/src/environments/environment.prod.ts` `siteUrl` | same host; it is baked at build time, so a host change needs a rebuild |
| `NEELASTACK_PUBLIC_URL` | `https://neelastack.com` |
| `AUTH_COOKIE_SECURE` | `true` |
| Razorpay keys | live key id + live secret (not `rzp_test_`), webhook secret equal to the dashboard value |
| Cashfree | live app id/secret with `https://api.cashfree.com/pg` |
| Cloudinary | real cloud name, key, secret (needed for any logo/banner upload) |
| `INITIAL_ADMIN_SETUP_ENABLED` | `true` only during first setup, then `false` |
| `BOOTSTRAP_ENABLED` | `false` (it would seed the demo "Dandiya Night" event; guard blocks it in production) |
| `IMAGE_NAMESPACE` / `IMAGE_TAG` | your GHCR owner / the 40-char release SHA (set by the pipeline) |
