# Neelastack Events 2.0.17

Enterprise production-hardening release following the v2.0.16 CI test-compilation defect.

## Key fixes
- Fixed the v2.0.15/v2.0.16 release-test compilation path and added cross-cutting v2.0.17 release contracts.
- Added Cashfree create-order idempotency and deterministic UUID idempotency keys for refunds; removed the fabricated customer phone fallback.
- Hardened Cashfree webhook semantic deduplication while retaining exact raw-body hashes for audit and preserving signature/timestamp verification.
- MFA attempt counters now survive invalid-code exceptions; TOTP replay is rejected with a persisted last-used counter and row locking.
- Login throttling is keyed to IP/client + email rather than email alone, reducing account-lockout abuse.
- Reduced checkout contention by avoiding an event-row lock across the inventory reservation phase and aligned checkout, inventory-admin and cancellation lock ordering to prevent deadlocks.
- Reservation expiry now drains bounded batches instead of processing only a fixed 200-row slice.
- Production boot now requires transactional email configuration and STARTTLS when SMTP auth is enabled.
- Actuator is isolated on the private management port and explicitly blocked at the public edge.
- CSV attendee exports now stream directly to the HTTP response; formula injection protection covers whitespace/control prefixes.
- Operations log raw-line fallback is redacted and uses a reusable HTTP client.
- robots.txt excludes operator/setup surfaces.
- Playwright now requires an explicit staging environment and rejects the live production hostname.
- V39 adds MFA replay state and validates the historical reservation-order foreign key.

## Qualification note
Static repository qualification is included in this release. Full Maven verification, Angular build, Docker image builds, Playwright E2E, load/chaos, backup/restore and provider sandbox qualification remain mandatory CI/staging release gates because those require the project toolchain, infrastructure and external services.

- Added durable event-change notifications for cancellations and buyer-visible schedule/venue/terms changes.
