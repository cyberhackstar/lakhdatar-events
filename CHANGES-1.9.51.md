# Neelastack Events v1.9.51 — Production Hardening

## Payment correctness
- Fixed late provider capture compensation when the local payment is still PENDING/FAILED/CANCELLED.
- Refund reconciliation now records provider capture/refund state before moving to REFUND_PENDING/REFUNDED.
- External Cashfree/Razorpay refund webhooks can materialize a local refund record instead of retrying forever with REFUND_NOT_LINKED.
- Partial external refunds remain REFUND_PENDING; only a full refund closes the payment as REFUNDED.

## Concurrency
- Replaced PaymentAttempt unique-key exception handling with PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`, avoiding aborted-transaction poisoning during concurrent webhook/reconciliation processing.

## Worker / HA
- Worker-only scheduled sweeps now explicitly check `app.worker.enabled` at execution time.
- HA API replicas now receive payment, mail, Cloudinary, public URL, secure-cookie and fail-closed rate-limit configuration required by production startup.

## Frontend payment flow
- Payment-result verification reduced to one initial call plus four spaced retries, staying within the five-per-minute server limit.
- Cashfree checkout mode is supplied by the backend from `CASHFREE_BASE_URL`, preventing frontend production/sandbox drift.
- Public API routes no longer attach an authenticated token or trigger token refresh, preventing forced-password-change users from blocking public browsing/checkout.

## Validation
- Static source checks performed after patching.
- Maven/Angular builds are attempted separately where the local environment permits; the final archive does not claim tests were executed unless their command completes successfully.
