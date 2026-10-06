# Neelastack Events v2.0.15 — Enterprise hardening + CI regression prevention

## Critical fixes
- Fixed the final v2.0.14 Java compilation errors in `EventManagementService` and `MfaService`.
- Added explicit MFA session proof to JWTs and refresh tokens; privileged refresh/password-change paths cannot bypass MFA.
- Forced existing sessions through MFA after MFA enrollment/reset.
- Restricted pre-MFA privileged bearer tokens to the MFA flow instead of allowing all authentication endpoints.
- Increased distributed-lock renewal worker capacity for concurrent background jobs.
- Configured an 8-thread scheduler pool to prevent independent maintenance/recovery jobs from serializing behind one scheduler thread.
- Fixed HA host allow-list for both `events.neelastack.com` and `monitor.neelastack.com`.
- Made the current two-node HA promotion workflow require exactly two nodes rather than accepting an undeployed node count.
- Added Flyway V38 for durable MFA session proof.

## Qualification intent
This release is source/CI hardening. Full production qualification must still execute the real Maven test suite, Angular build/E2E suite, staging payment flows, mixed 1,000+ user load, DAST, HA failover and PITR restore drill.

- Added JWT issuer/audience validation so tokens minted for another application cannot be accepted here.
- Added contract tests for MFA session proof, scheduler concurrency, lock renewal capacity and HA host configuration.

- Frontend auth now clears any stale in-memory/session-storage identity when credentials succeed but MFA is still pending, preventing identity carry-over during privileged reauthentication.
- HA compose injects and requires JWT issuer/audience consistently for API and worker replicas.
