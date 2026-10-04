# v1.9.23 — Enterprise hardening

This release hardens v1.9.22 without intentionally changing public API contracts.

## Correctness
- Serialize event-wide ticket inventory mutations on the parent event row.
- Add PostgreSQL capacity triggers as a database-level invariant backstop.
- Add reservation-to-order foreign-key integrity and supporting index.

## Reliability
- Replace the process-only ticket email queue with a durable PostgreSQL-backed outbox, bounded workers, retry/backoff, dead-letter state, history cleanup, and post-commit repair for crash-window recovery.
- Keep email delivery after transaction commit so mail failures cannot affect payment/ticket issuance.
- Move Cloudinary network uploads outside DB transactions and compensate remote uploads when DB persistence fails.

## Performance
- Stream attendee CSV exports with a forward-only PostgreSQL cursor.
- Cache rendered QR PNG data with a bounded LRU.
- Shard public sitemaps and route sitemap shards directly to the backend.
- Use sitemap slices to avoid a count query for every sitemap shard.

## Operations
- Add recovery-query indexes for the durable mail repair sweep (V23).
- Expose production DB, checkout, rate-limit, reservation, recovery, QR, and mail tuning through Compose and `.env.example`.
- Add regression contract coverage plus an integration test for concurrent event inventory edits.

## Validation note
The source package was audited and statically validated in the release workspace. Maven and Docker are not installed in the current execution environment, so the CI backend `mvn clean verify`, Angular production build/test, and ARM64 container build remain mandatory release gates.
