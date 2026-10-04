# Enterprise payment chaos qualification

Run only in staging with a disposable event and provider account. Never inject faults into a live payment campaign.

## Faults to inject

1. Provider DNS/connect timeout during order creation.
2. Provider HTTP 500 after request transmission.
3. Provider order created remotely but response lost.
4. Webhook delayed by 1–10 minutes.
5. Webhook delivered multiple times.
6. Refund response lost after the provider accepts the refund.

## Required invariants

- No duplicate provider order for one local order.
- No duplicate successful ticket issuance.
- A remote provider order found by receipt is adopted only after receipt, amount and currency match.
- An uncertain provider outage remains recovery-pending rather than being blindly recreated.
- A confirmed provider-not-found result may retry safely.
- Duplicate webhooks remain idempotent.
- Refund retries converge to one provider refund and one local refund result.
- Recovery backlog drains after the provider is restored.
