# Payment Gateway Architecture

Neelastack owns the payment orchestration layer. An event can select `RAZORPAY` or `CASHFREE`; the customer sees the selected provider's hosted checkout. Provider credentials remain server-side in the VM environment and are never exposed to Angular.

## Security contract

1. The backend calculates the authoritative amount from ticket inventory.
2. The backend creates the provider order/session.
3. The browser receives only a provider order/session identifier and public checkout key where required.
4. Razorpay browser responses require HMAC verification plus a server-side provider status fetch.
5. Cashfree redirect completion is verified by querying the Cashfree order payments API; client redirect parameters are not trusted as proof of payment.
6. Provider webhooks are signature-verified and idempotently persisted before processing.
7. Ticket fulfillment occurs only after a captured/successful provider payment matches the local amount and currency.
8. Refunds are queued and recovered asynchronously through the selected provider adapter.
9. An event's provider cannot be changed after payment activity exists.

## Environment

Configure only the provider credentials that are actually enabled. Use strong, private production secrets and keep them outside Git.

- Razorpay: `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`.
- Cashfree: `CASHFREE_APP_ID`, `CASHFREE_SECRET_KEY`. Cashfree webhook signatures are verified with the same provider Secret Key; this project does not define a separate Cashfree webhook secret.

Cashfree webhook endpoint: `/api/v1/webhooks/cashfree`; Cashfree orders also set the public `notify_url` to this endpoint and return to `/payment/success` for server-side verification.
Razorpay webhook endpoint: `/api/v1/webhooks/razorpay`.
