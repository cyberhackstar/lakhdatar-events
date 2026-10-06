# v2.0.23 — Clean-schema certification fix

## Scope
- Fix the real schema-validation blocker found by v2.0.22 `mvn clean verify`: `mfa_challenges.token_hash` was created as PostgreSQL `CHAR(64)` while JPA expects `VARCHAR(64)`.
- Normalize both authentication hash columns (`password_reset_tokens.token_hash` and `mfa_challenges.token_hash`) with a forward-only Flyway migration.
- Trim fixed-width padding during conversion.
- Align E2E package/lockfile versions with the repository release.
- Keep payment, order, ticket, webhook, QR, and API behavior unchanged.

## Compatibility
- Existing V34 remains immutable.
- Existing databases upgrade through V41.
- Fresh databases run V34 then V41 and converge on the same schema.

## Test-driven reason for this patch
The v2.0.22 backend run compiled 140 production sources and 64 test sources, but Spring context startup failed Hibernate schema validation on `mfa_challenges.token_hash` (`bpchar` vs `varchar(64)`).
