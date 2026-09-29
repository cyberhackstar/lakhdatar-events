# Phase 3 — Load, Security and Failure-Mode Test Runbook

## Scope

Verify the critical invariants before the first live event: single-use QR admission, inventory concurrency, payment recovery/idempotency, authorization isolation, rate limiting, and safe dependency-failure behavior.

## Automated integration tests

Run from `backend` in CI or an environment with Docker:

```bash
mvn -B -ntp clean verify
```

The Testcontainers suite includes concurrent ticket reservation and concurrent check-in coverage.

## k6 public event test

Use staging or controlled pre-production:

```bash
k6 run -e BASE_URL=https://staging.example.com -e EVENT_SLUG=dandiya-night-2026 infra/loadtest/public-event.js
```

## k6 concurrent check-in test

Use a **fresh, unused test ticket**. Never point the test at a live customer ticket.

```bash
k6 run \
  -e BASE_URL=https://staging.example.com \
  -e EVENT_ID=<event-public-uuid> \
  -e GATE='Main Gate' \
  -e STAFF_TOKEN='<short-lived-access-token>' \
  -e QR_TOKEN='<fresh-unused-ticket-credential>' \
  -e VUS=50 \
  infra/loadtest/checkin.js
```

The check-in test allows **at most one** accepted result for the same QR credential.

## Security matrix

Verify that a user cannot:

- access another organizer's event/order/ticket by changing identifiers;
- change ticket price or quantity through checkout JSON manipulation;
- validate an event-B ticket using event-A staff credentials;
- reuse an already checked-in QR;
- validate a refunded/cancelled ticket;
- brute-force tickets without rate limiting;
- use a rotated refresh token to mint a new session after reuse detection;
- retrieve tickets by guessing sequential IDs.

## Failure-mode drills

### Redis unavailable

Financial and ticket truth must remain in PostgreSQL. Distributed locks fail closed.

### PostgreSQL unavailable

No ticket admission is accepted and no successful local payment/ticket state is fabricated.

### Razorpay webhook delayed

Browser success is not the sole source of truth; reconciliation must converge local state with the provider.

### Payment captured after reservation expiry

No ticket is issued. The captured payment enters the controlled refund/recovery flow.

### Application restart during payment processing

Provider order/payment reconciliation can resume without duplicate ticket issuance.

## Production gate

Do not call the application live-event ready until CI/staging has passed:

- Maven unit/integration tests;
- Angular production build;
- ARM64 Docker image builds;
- concurrent reservation test;
- concurrent check-in test;
- payment signature/webhook tests;
- restore-from-backup drill;
- deployment smoke test;
- rollback drill;
- Android scanner test;
- iPhone scanner test;
- Razorpay test-mode purchase and recovery scenario.
