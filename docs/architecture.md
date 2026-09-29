# Architecture

## Runtime boundaries

The platform is a modular Spring Boot application backed by PostgreSQL and Redis. Angular SSR runs in a Node runtime behind the Nginx edge proxy. The public reverse proxy/tunnel exposes only the frontend entrypoint.

### Authoritative data

PostgreSQL owns:

- orders
- order items
- payments
- refunds
- inventory counters
- reservations
- tickets
- check-ins
- organizer/event ACL data
- audit logs

Redis is not authoritative.

## Critical flows

### Checkout

1. Client posts a bounded, validated cart plus a 16–100 character idempotency key.
2. Backend serializes the idempotency key with a PostgreSQL advisory transaction lock.
3. Event and ticket types are validated from server data.
4. Ticket types are row-locked and reservations are created.
5. Server-calculated amount is used to create the Razorpay order.
6. A local payment record is persisted.
7. Browser performs Razorpay checkout.
8. Backend verifies the Razorpay signature and fetches the provider payment.
9. Provider status must be `captured` and amount/currency/order must match local state.
10. Order confirmation and ticket issuance happen transactionally.

### Check-in

1. Staff authenticates with a short-lived JWT.
2. Backend verifies event/staff/gate authorization.
3. QR credential is parsed and verified.
4. Ticket row is locked with `SELECT ... FOR UPDATE`.
5. Status/event/payment-related eligibility is checked.
6. The ticket is changed to `CHECKED_IN` in the same transaction.
7. A check-in record and audit event are written.

The row lock plus transaction makes the admission decision atomic.

## Failure philosophy

When correctness is uncertain, fail closed for admission and fail recoverably for payments.

- Database unavailable → no check-in acceptance.
- Scanner offline → no check-in acceptance.
- Payment callback delayed → pending state + reconciliation, not a lost purchase.
- Notification failure → ticket remains valid.
- Deployment failure → rollback to previous image where possible.
