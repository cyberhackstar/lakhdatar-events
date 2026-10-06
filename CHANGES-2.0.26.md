# Changes — 2.0.26

## Integration-test secret isolation
- Replaced reliance on `@DynamicPropertySource` for fixed cryptographic test secrets with inherited `@TestPropertySource`.
- Test secrets now override developer/CI environment values deterministically during Spring integration-test context creation.
- Kept dynamic property registration only for PostgreSQL/Testcontainers connection details.
- No production runtime, API, schema, payment, refund, QR, or frontend behavior changed.

## Release consistency
- Active release version is 2.0.26.
