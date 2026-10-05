# Production incident runbooks

## Payment provider outage

Confirm provider health, stop non-essential retry storms, preserve idempotency keys, and keep customer-facing state recoverable. Do not create manual duplicate provider orders as a workaround.

## Refund backlog

Inspect pending/processing/manual-review counts, provider status, and `next_attempt_at`. Retry through the recovery service. A manual refund must be recorded against the existing payment/refund ledger.

## Database failure

Protect the latest healthy node, fail over to the managed/standby database, verify Flyway/schema state, and run business invariants before admitting traffic.

## Redis failure

Follow the configured rate-limit and worker failure policy. Stateful business correctness must remain PostgreSQL-backed. Validate distributed-lock behavior before restoring normal worker concurrency.

## Bad deployment

Stop promotion, preserve logs and release SHA, verify whether Flyway migrations changed the schema, and use the expand/contract rollback procedure. Do not blindly downgrade a schema-changing release.

## Suspected credential compromise

Revoke affected sessions/secrets, rotate provider/application credentials, preserve audit evidence, assess access logs, and perform a post-incident review before resuming privileged operations.

Every incident should record detection time, mitigation time, customer impact, RTO/RPO, release SHA, root cause and corrective actions.
