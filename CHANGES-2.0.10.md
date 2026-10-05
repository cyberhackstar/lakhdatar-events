# Neelastack Events v2.0.10 changes

## Phase 1 — financial correctness and event concurrency

- Corrected event-cancellation refund candidate selection so a previous `FAILED` refund does not permanently suppress a later retry.
- Added bounded refund retry backoff, attempt ceilings, processing timeouts, and explicit manual-review state.
- Made system-driven event-cancellation refunds independent of attendee check-in state so cancellation can still return captured customer funds.
- Replaced per-ticket cancellation with set-based PostgreSQL updates.
- Released held reservations and returned reserved inventory in a set-based event-cancellation operation.
- Made the final ticket check-in write conditional on the event still being `PUBLISHED` to close the cancellation/check-in race.

## Phase 2 — operational resilience and release qualification

- Production backup policy now fails closed for existing deployments unless independent remote backup storage is configured and verified.
- Added an explicit disaster-recovery restore drill script with checksum verification and core-schema probes.
- Added optional read-only production ticket and PDF smoke tests.
- Added an enterprise staging load gate that requires all critical-path credentials and correctly passes the configured 1,000-user profile into k6.
- Added a deployment guard requiring explicit acknowledgement when the single-node production profile is used.

## Phase 3 — privileged security and payment webhook reliability

- Added encrypted TOTP MFA for privileged roles with DB-backed one-time challenges and attempt limits.
- Added password recovery with hashed single-use tokens, rate limits, session revocation, and fragment-only reset URLs.
- Added ADMIN-only emergency MFA reset for a different privileged operator and corresponding audit logging.
- Added durable asynchronous processing for Razorpay and Cashfree webhooks so payment-provider acknowledgement is decoupled from slow business processing.
- Added provider discrimination to shared webhook storage, jittered retry backoff, bounded attempts, stale-claim recovery, and dead-letter state.
- Added frontend auth lifecycle regression tests for pending MFA and password recovery.


## Phase 4 — operational certification and backup integrity

- Corrected S3-compatible backup bucket/prefix parsing and added verification of both the dump and checksum object.
- Added explicit backup-at-rest encryption selection (`AES256` by default or AWS KMS) instead of relying only on bucket defaults.
- Production deploy smoke can run in enterprise mode and fails closed when no dedicated issued ticket/token is supplied for read-only ticket qualification.
- Added current-version release validation evidence and upgraded the production checklist to require refund, concurrency, E2E, load, PITR and privileged-MFA evidence.
- Added bounded terminal webhook retention with worker-only/distributed-lock execution and documented data lifecycle policy.
