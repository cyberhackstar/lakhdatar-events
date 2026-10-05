# Financial reconciliation and control policy

The payment provider is not the system of record for application fulfillment. PostgreSQL is the application source of truth for orders, payments, refunds and tickets; provider APIs/webhooks are reconciled against it.

Daily control totals should compare, per provider and event where applicable:

- successful/captured payments
- application-completed payments
- refunds completed by provider vs application
- pending/stale payment recovery
- refund manual-review queue
- provider settlement totals when available

Any mismatch must be investigated before the event is financially closed. High-risk adjustments require an operator identity and an append-only audit entry. Never resolve a discrepancy by editing payment/ticket rows directly; use the domain recovery/reconciliation path.
