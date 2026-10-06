# v2.0.23 Validation

## v2.0.22 failure addressed
The prior release reached test execution but failed application-context creation because Hibernate found `mfa_challenges.token_hash` as PostgreSQL `bpchar` while the entity contract expects `varchar(64)`.

## Fix
V41 converts both auth hash columns to `VARCHAR(64)` using `RTRIM(token_hash)`, preserving SHA-256 hex values while removing CHAR padding.

## Regression protection
- Migration contract verifies both columns are converted.
- Existing V34 is not edited, avoiding Flyway checksum drift.
- E2E package/lockfile versions are aligned to 2.0.23.

## Required external gates
Run `mvn -B -ntp clean verify` on the v2.0.23 tree. The authoritative result remains the user's local/CI environment.
