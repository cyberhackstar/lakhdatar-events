# Enterprise data lifecycle baseline

Neelastack Events must treat PostgreSQL as the system of record. Do not delete or rewrite financial, ticket, check-in, or audit facts simply to control table size.

## Hot data policy

- `payments`, `refunds`, `orders`, `tickets`, `ticket_checkins`, and immutable financial ledger rows remain queryable in the primary database for the period required by the business, tax, accounting, and legal retention policies.
- `payment_webhook_events`, provider attempt history, and delivery telemetry may be archived after the hot operational window, but only after the corresponding payment/refund state is terminal and reconciliation has completed.
- `audit_logs` remain append-only and are retained according to the operator's legal/security policy.

## Scale controls

Use the existing cursor APIs for high-volume admin lists. For very large histories, archive by time/event boundaries rather than deleting individual rows inside request transactions. Add PostgreSQL partitioning only after measuring actual table growth; partition keys must preserve existing ledger and audit semantics.

## Operational requirements

Every retention job must be worker-only, idempotent, bounded by batch size, and observable. Never run destructive retention automatically against an unverified production database. Backup/PITR coverage must include the full retained financial record set.
