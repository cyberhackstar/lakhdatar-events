# Final Production Audit — v1.9.7

## User-reported failure addressed

The user-side v1.9.6 full `mvn -B -ntp clean verify` run compiled 102 production Java classes and 33 test classes. All tests reached execution and the Spring contexts started successfully. The sole remaining test error was `CheckInConcurrencyTest.exactlyOneAcceptedUnderConcurrentScans`, which failed with `ApiException: Too many scan requests`.

## Root cause

`AbstractPostgresIntegrationTest` intentionally does not provide Redis and documents that `RateLimitService` should fall back to its bounded in-memory limiter. However, the integration test inherited the production setting `app.rate-limit.fail-closed-on-redis-error=true`. With Redis unavailable, every scan correctly failed closed before the concurrency test could exercise ticket row locking.

## v1.9.7 correction

- Added an integration-test-only dynamic property: `app.rate-limit.fail-closed-on-redis-error=false`.
- Kept production `RATE_LIMIT_FAIL_CLOSED=true` unchanged.
- No production authorization, inventory, payment or rate-limit policy was weakened.
- Existing Redis timeout reductions remain in place for integration tests.
- Release metadata bumped to 1.9.7 without changing third-party dependency versions.

## Validation performed in this environment

- Baseline verifier: PASS.
- Production Java files: 102.
- Test Java files: 33.
- POM/frontend JSON parsing: PASS.
- Flyway sequence: V1..V17.
- Package-lock semantic diff vs v1.9.6: only `/version` and `/packages//version`.
- No secret-like patterns detected by release scan.
- Archive hygiene/integrity checks prepared for final package.

## Runtime/build boundary

The user's Windows Maven/Testcontainers output is authoritative for dependency-backed runtime tests. This audit environment does not provide Docker/Maven execution, so the final `clean verify` result must be confirmed on the user's machine/CI.
