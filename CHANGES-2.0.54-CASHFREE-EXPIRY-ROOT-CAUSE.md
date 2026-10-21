# v2.0.54 — Cashfree expiry root-cause fix

## What failed
Staging browser E2E (run 39548565146, SHA 962eb33, v2.0.53): `checkout idempotency returns the same logical order on retry` got HTTP 502 instead of 200 on the first `POST /api/v1/public/checkout`. 19 passed, 1 failed, 6 skipped and 1 did not run (the skipped/not-run tests depend on a successful checkout). Same root cause as v2.0.52: Cashfree Create Order returns 400 `order_expiry_time_invalid`, which the app maps to 502 `PAYMENT_PROVIDER_ERROR`.

## Why 2.0.53 did not fix it
- v2.0.50 (last clean) never sent `order_expiry_time`. v2.0.52/2.0.53 started forwarding `reservationExpiresAt`, which is `now + RESERVATION_HOLD`.
- 2.0.53 documented "default hold raised from 12m to 20m", but **no config was changed**: `application.yml`, `docker-compose.staging.yml`, `docker-compose.prod.yml`, `ha/docker-compose.ha.example.yml`, `.env.example` and `.env.staging.example` all still said `12m`. The 2.0.53 CHANGES file also told operators to keep `RESERVATION_HOLD=12m` in `STAGING_ENV_FILE`. So staging kept sending an expiry 12 minutes out, which Cashfree rejects.
- The nanosecond-precision theory in 2.0.53 was not the cause; 12 minutes of lead time was.

## Changes
1. `RESERVATION_HOLD` default is now `20m` everywhere (application.yml, staging/prod/HA compose, env templates).
2. `AppProperties.Reservation.effectiveHold()` floors the hold at 16 minutes; `OrderService` uses it, so a stale `12m` override can no longer produce an invalid provider expiry, and local reservation and provider expiry stay aligned.
3. `CashfreeGatewayProvider.formatOrderExpiry()`: ISO-8601 with `+05:30` offset (the format in Cashfree's own examples), whole seconds always present, and never less than 16 minutes from now (defence in depth).
4. Tests: updated expected wire format; added `createOrderNeverSendsExpiryCloserThanProviderMinimumLead` and `reservationHoldIsNeverBelowProviderSafeFloor`.
5. `TicketTypeRepository` projection formatting cleaned (no behaviour change). Version bumped to 2.0.54 across VERSION, pom, package(-lock) files, compose files, env templates, certification script and manifest.

## Deployment requirement
In the protected `STAGING_ENV_FILE` and the production env file, **remove `RESERVATION_HOLD=12m` or set `RESERVATION_HOLD=20m`**. (The code now floors it at 16m regardless, but fix the file so config and behaviour agree.) Do not print the file.

## Not verified here
Maven is not available in this environment, so the backend was not compiled or tested here. The Cashfree minimum lead time is not stated consistently in Cashfree's public docs (one page says 5 minutes), so 16m is a deliberately conservative choice based on the observed rejection at 12m. Confirm with a real sandbox checkout.
