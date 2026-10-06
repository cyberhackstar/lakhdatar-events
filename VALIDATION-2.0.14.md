# Neelastack Event Platform v2.0.14 — Validation Record

## Why v2.0.14 exists

The supplied GitHub Actions logs for v2.0.13 exposed two independent classes of failures:

1. Backend compilation reached javac but failed on `RefundRepository` (`Instant`), `PasswordResetController` (`RequestBody` name collision), `JwtAuthFilter` (`User::isEnabled`), and a large cascade of missing Lombok-generated accessors/mutators.
2. CodeQL's Java matrix attempted the Maven build without Java 21 and failed with `release version 21 not supported`.

## v2.0.14 fixes

- Added `java.time.Instant` import to `RefundRepository`.
- Renamed the password reset request DTO to `PasswordResetRequest`.
- Pinned Lombok to 1.18.48 and configured Maven annotation processing explicitly with `proc=full` and an annotation processor path.
- Provisioned Java 21 in the CodeQL Java matrix before Maven execution.
- Added release-consistency checks for backend/frontend/e2e versions.
- Synchronized active production documentation to v2.0.14.

## Local static validation

- `node tools/verify-platform-baseline.mjs` — PASS
- `node tools/verify-frontend-lock.mjs` — PASS
- JSON parsing — PASS
- YAML parsing — PASS
- XML parsing — PASS
- Shell syntax — PASS
- JavaScript syntax — PASS
- Java delimiter balance — PASS
- Active-version consistency sweep — PASS
- Maven workflow/toolchain checks — PASS

## Required target-environment qualification

These cannot be truthfully claimed from this offline build environment and must run in GitHub Actions/staging:

- `mvn -B -ntp clean verify`
- Angular production build and unit tests
- Flyway fresh/upgrade qualification
- Playwright critical-path E2E
- mixed k6 load qualification
- payment sandbox/chaos qualification
- DAST
- HA failover and rollback drill
- PostgreSQL PITR restore drill
- real alert/paging validation
- independent penetration test

Production promotion remains fail-closed on the protected enterprise certification evidence gate.
