# Neelastack Event Platform 2.0.26 — CI Fix

## Fixed

- Repaired `frontend/package-lock.json` to use the published `colorette` 2.0.20 artifact required by `listr2` 9.0.1; added a regression guard to prevent the unavailable Colorette archive from returning.
- Updated the HA contract test to resolve committed repository fixtures independent of the Maven working directory.
- Explicitly unignored the safe `infra/ha/.env.ha.example` template so it can be committed; it contains placeholders only.
- Removed the hard-coded MFA encryption key from `AbstractPostgresIntegrationTest`; the integration suite now derives a deterministic 32-byte test key at runtime.
- Updated the HA example release version to 2.0.26.

## Validation available in this environment

- JSON parse and lockfile consistency checks performed locally.
- Direct Maven/Gitleaks execution is unavailable in this container because `mvn` and `gitleaks` are not installed. GitHub Actions remains the authoritative full CI environment.


## Follow-up regression fix
- Corrected event cancellation persistence after bulk ticket/reservation updates clear Hibernate's persistence context.
- Re-fetches the locked event after cancellation bulk operations before setting `CANCELLED`.
- Strengthened `MultiEventCatalogIntegrationTest` to verify `CANCELLED` is persisted before checking publish rejection.

## Release certification / concurrency follow-up
- Hardened source-contract tests to normalize formatting/casing before checking required SQL and lock-order invariants; test failures are no longer caused by harmless Java formatting or alias choices.
- Standardized checkout ticket-type lock acquisition by persistent database ID before the event lock, matching cancellation and admin inventory mutation paths.
- This removes the cross-flow lock-order mismatch between checkout's previous UUID ordering and cancellation/admin database-ID ordering.

## Final compile / certification correction
- Fixed `OrderService.createLocalCheckout` variable shadowing by renaming the `TicketTypeRequest` loop variable from `request` to `typeRequest`; this preserves the public `CheckoutRequest request` parameter and removes the Java compilation error.
- Updated `EnterpriseReleaseV220ContractTest` to validate the checkout reservation call by semantic pattern instead of requiring a specific local variable name.
