# Validation 2.0.24

## Change scope

This release is a test-environment reliability fix. Production runtime behavior is unchanged.

## Expected CI gates

- `mvn -B -ntp clean verify` should no longer depend on a developer `JWT_SECRET` environment variable for integration tests.
- Flyway migration chain remains unchanged from V41.
- Existing payment, authentication, QR, refund, and inventory contracts remain in place.

## Local verification

- Added hermetic test properties in `AbstractPostgresIntegrationTest`.
- Active release metadata bumped to 2.0.24.
- No historical change logs were rewritten.
