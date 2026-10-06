# Neelastack Event Platform v2.0.14 — CI/build and production-readiness correction

## Build correctness

- Explicitly enabled Maven annotation processing for Java 21+.
- Pinned Lombok to 1.18.48 and configured it as a compile-time `provided` dependency plus explicit annotation processor.
- Added the missing `java.time.Instant` repository import.
- Renamed the password-reset request record to avoid collision with Spring's `@RequestBody` annotation.
- Provisioned Java 21 in the CodeQL Java matrix before Maven compilation.
- Kept CodeQL Java compilation aligned with the backend Maven command instead of CodeQL autobuild heuristics.

## Release consistency

- Active application/package versions synchronized to 2.0.14.
- Production/HA documentation synchronized to 2.0.14.
- Release verification updated to enforce the Lombok/JDK21 build invariants.

## Enterprise policy

v2.0.14 is production-hardened source and certification-gated. It must not be represented as runtime-certified until the protected CI/staging qualification evidence is actually produced.
