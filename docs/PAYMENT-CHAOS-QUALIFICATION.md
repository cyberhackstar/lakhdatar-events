# Payment and recovery qualification

Use provider sandboxes and disposable staging data. Never run these scenarios against live customer orders.

## Required scenarios

- Browser closes after checkout creation; recovery finds the same provider order.
- Verify request is duplicated; only one confirmation/fulfillment occurs.
- Webhook arrives before browser verification.
- Webhook arrives multiple times and out of order.
- Provider is temporarily unavailable during refund.
- Refund request fails, then succeeds on a later recovery pass.
- Event cancellation occurs while refund recovery is running.
- Database restart occurs between payment persistence and webhook processing.
- Redis restart occurs while rate limiting/distributed jobs are active.

Pass only when order state, payment state, inventory, ticket count, and refund ledger remain internally consistent and no money is created/destroyed by retries.
