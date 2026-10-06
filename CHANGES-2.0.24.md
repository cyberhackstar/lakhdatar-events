# Neelastack Events 2.0.24

## Enterprise test reliability fix

- Made PostgreSQL-backed integration tests hermetic by supplying non-production cryptographic test properties through `@DynamicPropertySource`.
- Test-only JWT, ticket-view, QR signing, and MFA encryption values are injected only into the integration-test JVM.
- Production configuration remains fail-closed and continues to require real secrets.
- Bumped active release metadata consistently to 2.0.24.

## Regression protection

- No production API, payment state machine, database schema, or frontend behavior changed in this release.
