# Validation — 2.0.26

- Version: 2.0.26
- Integration tests use inherited `@TestPropertySource` for fixed cryptographic test values.
- V41 migration chain unchanged.
- No production runtime or API changes.
- Backend `mvn -B -ntp clean verify` remains the authoritative developer/CI gate.
## Post-build correction — JWT configuration binding

- `AppProperties.Jwt` contains both the canonical five-argument record constructor and a three-argument convenience constructor.
- The canonical constructor is now explicitly annotated with `@ConstructorBinding`, matching the other nested configuration records with convenience constructors.
- `AppPropertiesBindingTest` now verifies `app.jwt.secret`, issuer, and audience are bound to the `Jwt` record.
- This prevents integration-test `ApplicationContext` startup from constructing `JwtService` with an empty/incorrectly bound JWT secret.
