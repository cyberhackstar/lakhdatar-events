# v2.0.53 — Cashfree order-expiry compatibility

## Evidence

Staging backend logs for the v2.0.52 checkout failure show Cashfree Create Order (`POST /pg/orders`) returning HTTP 400 with provider code `order_expiry_time_invalid`. The application translated this provider error into HTTP 502 `PAYMENT_PROVIDER_ERROR`; Playwright then failed before reaching its retry assertion. This is an upstream expiry-field rejection, not a Git or Gitleaks failure.

## Changes

- Raise the default local reservation hold from 12 to 20 minutes so the expiry passed to Cashfree has additional forward-time headroom.
- Normalize Cashfree's `order_expiry_time` serialization to whole-second UTC precision (`Instant` can contain nanosecond fractions when created at runtime).
- Add a regression assertion proving fractional nanoseconds are not forwarded.
- Keep local reservation expiry and provider expiry aligned; do not simply omit the provider expiry, as that can allow a payment order to remain valid after local ticket inventory is released.

## Deployment configuration requirement

`RESERVATION_HOLD` is a runtime environment override. The staging deployment secret `STAGING_ENV_FILE` should retain `RESERVATION_HOLD=12m` for this qualification. Confirm the effective value after deployment; do not change the reservation duration while testing timestamp precision. Do not print or paste the full `STAGING_ENV_FILE`.

The supplied diagnostic also showed the Cashfree App ID had a `TEST` prefix while the secret key did not. This did not cause the reported field-specific error, but verify in the Cashfree merchant dashboard that both values are a matching sandbox credential pair. Never substitute live credentials for staging.

## Release status

Candidate only. Run backend Maven verification and then staging deployment + full mutation-enabled E2E. Confirm the first checkout returns success, retry resolves to the same logical order, payment confirmation works, and the generated ticket can be scanned. The provider's prior 400 response means the fix is not confirmed until the real sandbox request succeeds. No existing payments or provider orders are modified.
