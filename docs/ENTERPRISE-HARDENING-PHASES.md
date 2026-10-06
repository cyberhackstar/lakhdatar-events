# Neelastack Events v2.0.15 — Enterprise hardening phases

## Phase 1 — Financial and event-state correctness

**Status: implemented in source**

Refund cancellation recovery is retry-safe after provider failures, refund attempts are bounded, manual-review states are explicit, cancellation is set-based for tickets/reservations, and the final check-in write is conditioned on the event remaining published.

## Phase 2 — Operational resilience

**Status: implemented in source; target-environment proof required**

Existing-data deployments require independent remote backups, backup object verification and restore-drill tooling. Deployment topology is explicit, single-node use requires acknowledgement, and staging load qualification can run the 1,000-user profile.

## Phase 3 — Privileged security and payment reliability

**Status: implemented in source**

Privileged MFA uses encrypted TOTP secrets and one-time DB-backed challenges. Password recovery uses hashed single-use tokens and revokes active sessions. Razorpay/Cashfree webhooks are persisted before acknowledgement, processed asynchronously, provider-scoped, retried with backoff/jitter, stale-claim recovered and dead-lettered after bounded attempts.

## Phase 4 — Certification and production safety

**Status: implemented in source; target-environment proof required**

Backup uploads use explicit encryption and verify both objects. Enterprise production smoke can fail closed unless a dedicated live-issued ticket/token is available for read-only ticket/PDF validation. Terminal webhook retention is bounded and worker-only. Release evidence now explicitly requires browser E2E, payment chaos, load, PITR restore and HA failover evidence.

## What cannot be guaranteed by source code alone

No source package can prove multi-failure-domain HA, managed database failover, payment-provider availability, real device camera compatibility, 1,000-user latency SLOs, or a restore RPO/RTO. Those are release-certificate evidence from CI/staging/production infrastructure and must be executed before public launch.
