# Changes — v1.9.52

This release closes the remaining v1.9.51 production blockers and hardens payment/refund consistency, missed-webhook recovery, worker isolation, scanner privacy, and deployment safety.

## Payment and refund integrity
- Razorpay refund webhooks are authenticated, idempotently stored, linked by provider payment ID, amount-validated, and reconciled locally.
- Periodic reconciliation also checks already-completed payments for provider refunds, so a missed dashboard/webhook delivery cannot silently leave a refunded sale locally fulfilled.
- Reconciliation and refund execution use deterministic provider-payment selection; ambiguous matches fail closed instead of using an arbitrary `findFirst()`.
- Refunds support multiple provider refund records per payment, including cumulative amount protection for partial refunds.
- Existing local full-refund behavior refunds only the remaining unrecovered amount when a payment already has completed partial refunds.
- Receipt-based recovery immediately submits a queued refund after a late captured payment is detected.

## Worker isolation
- API replicas can enqueue durable ticket-mail jobs without submitting local background work when `WORKER_ENABLED=false`.
- Worker-only scheduled recovery components have both Spring conditional configuration and runtime guards.

## Authentication and privacy
- Forced-password-change accounts can still reach public/catalogue, webhook, setup and health endpoints when a stale bearer token is attached; business APIs remain blocked until password change.
- Wrong-event and invalid-credential scans retain audit linkage but no longer return attendee/ticket PII in the API response.

## Deployment safety
- Existing deployments now require a successful PostgreSQL backup and verification before deployment proceeds.
- First-install remains the only path where a missing running PostgreSQL container is tolerated.
- Release packages exclude local `node_modules`, Angular build cache, and Maven target artifacts.

## Release health
- Stale refund contract expectations were replaced with checks for the new provider-refund behavior.
- Version metadata and release documentation consistently identify `1.9.52`.
