# v2.0.22 Validation

## Scope
- v2.0.21 retained as functional baseline.
- Fix is limited to Flyway V33 syntax, release-contract drift, and active version metadata.

## Regression fixes
- V33 now uses valid PostgreSQL `ALTER TABLE ... VALIDATE CONSTRAINT` syntax.
- Enterprise contract assertions now match the current implementation without weakening safety invariants.
- Release metadata/defaults are aligned to 2.0.22.

## Required next gate
Run `mvn -B -ntp clean verify` from `backend` on the user's machine/CI. Do not proceed to frontend or deployment until it passes.

## Not claimed here
- Provider sandbox E2E
- k6 load tests
- HA failover
- PITR restore
- DAST/security infrastructure tests
