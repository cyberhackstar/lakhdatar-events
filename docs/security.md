# Security

## Authentication

- BCrypt password hashing with cost 12.
- Short-lived access JWTs.
- Refresh tokens are random, rotated and stored only as SHA-256 hashes.
- Login rate limiting uses Redis with a local fallback.

## Authorization

Backend enforces role and object-level authorization. Organizer owners can manage their organizer's events. `EVENT_MANAGER` users require an explicit event assignment and cannot inherit event access from organizer membership. Staff can only check in assigned events and gates.

## QR credentials

The QR contains an opaque, signed credential derived from the ticket public UUID. Raw credentials are never stored in the database; only a hash is persisted.

## Payment security

- Razorpay checkout signature verification is server-side.
- Provider payment state, order ID, amount and currency are checked before fulfillment.
- Razorpay webhooks are HMAC-verified.
- Webhook processing is persisted and duplicate-safe.
- Reconciliation can recover a locally pending order from the provider.

## Secrets

No production secret belongs in source control or the Angular bundle. Use VM environment/secrets management. Rotate bootstrap credentials after first setup.

## Data leakage prevention

Do not log:

- passwords
- refresh tokens
- JWT signing keys
- QR raw credentials
- Razorpay secrets
- unnecessary payment/customer data

## Edge security

The included Nginx config applies HSTS, frame protection, content-type protection, referrer policy, a restrictive CSP and a camera-only Permissions Policy. Public production traffic must use HTTPS.

## Threats explicitly considered

- IDOR / cross-event access
- price tampering
- QR token guessing/replay
- duplicate webhooks
- duplicate check-in
- inventory overselling
- brute-force login/scans
- browser disconnect after payment
- Redis failure
- database failure
- bad release rollback
