# Payment lifecycle

## Local states

```text
CREATED → PENDING → CAPTURED → COMPLETED

PENDING → FAILED
PENDING → CANCELLED
COMPLETED → REFUND_PENDING → REFUNDED
```

## Checkout verification

The browser provides `razorpay_order_id`, `razorpay_payment_id` and `razorpay_signature` to the backend. The backend:

1. finds the local payment by Razorpay order ID
2. verifies the HMAC signature
3. fetches the provider payment
4. checks provider order ID, amount and currency
5. requires provider status `captured`
6. records the provider payment ID
7. transactionally confirms the order and issues tickets

## Webhooks

`POST /api/v1/webhooks/razorpay` verifies the Razorpay webhook signature over the raw body. The event body is persisted before business processing. Duplicate bodies are rejected as already seen, allowing safe webhook redelivery.

## Reconciliation

A scheduled sweep asks Razorpay for payments attached to old local `PENDING`/related states. A matching captured payment is reconciled into the local order and ticket flow.

## Operational rule

Never mark an order successful solely because Angular received a checkout callback. Never issue a ticket for an unverified provider state.


## Webhook delivery semantics
Razorpay webhook delivery is treated as at-least-once and may be duplicated or arrive out of order. The backend persists provider event IDs, atomically claims webhook work, and releases failed claims so provider retries can recover incomplete processing.
