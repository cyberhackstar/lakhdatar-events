# Lakhdatar Events v1.9.7

Release type: production verification / integration-test reliability patch

## Fixed in v1.9.7

- Fixed the integration-test Redis/rate-limit mismatch that caused CheckInConcurrencyTest to fail with `Too many scan requests`.
- Integration tests explicitly use the bounded in-memory rate-limit fallback when Redis is intentionally unavailable.
- Production keeps `RATE_LIMIT_FAIL_CLOSED=true` by default; production rate limiting is not weakened.
- Kept the Spring Boot request-context filter collision fix from v1.9.5/v1.9.6.
- Kept the AppProperties nested-constructor binding hardening and QR configuration validation.
- Kept the payment/session/refund/authorization hardening from the 1.9.x enterprise releases.
- Corrected release metadata to 1.9.7 across VERSION, Maven, frontend package metadata, release manifest and baseline verifier.
- No third-party npm dependency versions were changed by the release bump.

## Verification

- Production Java sources: 102.
- Test sources: 33.
- Flyway migrations: V1..V17.
- Baseline verifier: PASS.
- Release-version consistency: PASS.
- Frontend package-lock semantic diff versus v1.9.6: application root version only.
- No production source rate-limit fail-open behavior was introduced.

## User-side verification

The v1.9.6 full `mvn -B -ntp clean verify` run compiled all 102 production classes and 33 tests. The Spring application contexts now start and the integration tests execute; the only remaining failure was `CheckInConcurrencyTest.exactlyOneAcceptedUnderConcurrentScans`, where Redis was intentionally absent but the inherited production fail-closed setting rejected the concurrent scans.

Verify v1.9.7 from a clean extraction with:

`mvn -B -ntp clean compile`

`mvn -B -ntp clean verify -Punit`

`mvn -B -ntp clean verify`

The final command requires Docker for PostgreSQL/Testcontainers.
