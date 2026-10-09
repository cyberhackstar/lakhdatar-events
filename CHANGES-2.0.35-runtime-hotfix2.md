# Neelastack Events v2.0.35 — Runtime Hotfix 2

## Root cause fixed

Checkout was returning HTTP 502 with `PAYMENT_PROVIDER_UNAVAILABLE` while the backend had already created the local order. The underlying failure was not the edge proxy: Spring Boot ECS structured logging threw `IllegalStateException: Duplicate nested pairs added under 'provider'` when an event contained both the scalar `provider` field and dotted `provider.*` fields such as `provider.path`.

The logging exception masked the original Razorpay provider failure and caused the application to surface a misleading payment-provider-unavailable response.

## Correction

- Harden `EnterpriseLog` so a scalar `provider` value is emitted as `provider.name` whenever the same event contains a `provider.*` field.
- Preserve legacy scalar `provider` output when there are no nested provider fields.
- Update the operations log reader to prefer `provider.name` and fall back to legacy `provider` values.
- Add a regression test proving that `provider` + `provider.path` no longer produces the ECS key collision.
- Keep provider paths, HTTP status and duration visible for the subsequent real provider error diagnosis.

## Scope

No payment business rules, inventory logic, ticket issuance, authentication, database schema, QR validation, or provider credentials were changed. This hotfix only prevents structured logging from masking provider failures and improves compatibility of the operator log reader.
