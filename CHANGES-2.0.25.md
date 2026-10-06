# Changes — 2.0.26

## Test-harness reliability fix
- Register cryptographic test properties independently of PostgreSQL container availability.
- Prevent integration tests from falling back to production-style placeholder JWT/security secrets during ApplicationContext startup.
- No production runtime, API, database migration, payment, or frontend behavior changes.

## Release consistency
- Active release version is 2.0.26 across root/backend/frontend/E2E metadata.
