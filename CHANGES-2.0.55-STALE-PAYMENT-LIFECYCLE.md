# Changes — v2.0.55 stale-payment lifecycle hardening

## Problem evidence

Staging checks reported 2,128 old pending-payment warnings while the ten data-integrity failure counters were zero. The majority were Cashfree PENDING with provider order/session IDs but no provider payment ID. A separate safe diagnostic showed 788 AWAITING_PAYMENT orders whose reservation rows were already RELEASED. A processed webhook inventory contained only one record. This did not establish the actual Cashfree transaction state.

## Changes

1. Add a bounded scheduled order repair for open orders older than 60 seconds that have reservation records but no currently live HELD reservation. It changes only the order lifecycle and checkout session hash. It does not mark any payment failed or clear a payment warning on its own.
2. Add a bounded historical reconciliation lane for old records (the 7–90 day window), attached to locally EXPIRED/CANCELLED orders. Keep the normal seven-day reconciliation lane separate, cap both batches, and rotate eligible work.
3. For a candidate with no selected captured/refunded provider attempt, read the provider order and only terminalize when the provider independently says the order is expired/cancelled/failed/closed, all returned payment attempts are known terminal non-captured statuses (or the provider returns an empty attempt list), and local receipt/order/amount/currency are consistent. If there is a network error, 404/missing resource, unknown/in-flight status, capture or refund signal, leave the payment unresolved.
4. Keep late captured-payment handling intact: the existing reconciliation path performs amount/currency checks and routes captures after reservation/event closure through queued refund compensation, not ticket issuance.
5. Preserve the historic database column `last_reconciled_at` for migration compatibility; explicitly document it as last attempt, not proof of success.
6. Bundle the previously separate readiness kit and read-only evidence inventory script into the source archive.

## Deliberately unchanged

- No migration, direct production database edits, test fixture data, real payment credentials, protected GitHub readiness status, or production certification value is added.
- The staging deployment still requires its deploy target/SSH key/known-hosts pin and complete stage-only environment file.
- The stateful staging browser suite still requires a dedicated least-privilege `E2E_STAGING_PROVISIONING_CONFIG` with pre-enrolled staging MFA. Do not merge it with the runtime environment secret.
- Independent penetration testing, payment-chaos testing, finance review, privacy review and privileged MFA review remain separate gates.
