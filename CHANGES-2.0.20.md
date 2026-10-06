# Changes v2.0.20

## Phase 4 — HA / PITR certification hardening

- Strengthened PITR readiness to verify live WAL archive delivery through `pg_stat_archiver`.
- Added a configurable maximum acceptable age for the latest archived WAL.
- PITR archive failures now fail the readiness gate unless explicitly reviewed and overridden.
- Strengthened HA preflight to require a live HTTPS ingress health check.
- HA preflight now requires encrypted external PostgreSQL and Redis dependencies.
- Added release contract coverage for the HA/PITR certification gates.
- Retained v2.0.19 payment bulkheads, provider circuit breakers, staging checkout qualification, and adaptive recovery workers.

## Qualification status

Static and contract validation is included in this package. Real HA failover, PITR restore, provider sandbox, load, browser, and chaos evidence must still be produced by protected CI/staging infrastructure.
