# Changes — v1.9.53

## Release-blocking CI fixes

- Fixed `OrderService.recordProviderAttempt` to use the actual `ProviderPayment.errorDescription()` component instead of the removed/nonexistent `message()` accessor.
- Fixed `CashfreeWebhookService` status normalization to use `Enums.PaymentStatus` before comparing/serializing the normalized value.
- Kept provider-attempt persistence PostgreSQL-safe with `ON CONFLICT DO NOTHING`.
- Kept v1.9.52 refund, reconciliation, HA worker, check-in PII, and backup hardening intact.
- Bumped release metadata consistently to `1.9.53`.

## Qualification intent

This release is specifically intended to make the v1.9.52 hardening branch compile cleanly under Java 21/Maven CI and then proceed to the existing test suite.
