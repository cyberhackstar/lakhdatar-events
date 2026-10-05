# Validation — v1.9.54

## Incident addressed

The v1.9.53 GitHub Actions backend job reached the full Maven test phase after successful Java compilation, then failed exactly two tests:

- `TeamAuthContractTest.initialPasswordAccountsAreBlockedUntilTheyChangeIt`
- `MultiEventCatalogIntegrationTest.lifecycleTransitionsAreGuarded`

The first failure was a brittle source-text assertion that expected an implementation detail removed during the password-change boundary refactor. The production filter itself already enforced the boundary through `isAllowedDuringPasswordChange(...)`.

The second failure occurred because the integration test attempted to publish an event with the production publication policy while no merchant payment credentials existed in the test environment. The production policy correctly rejects an event whose selected provider is not configured.

## v1.9.54 corrections

1. `TeamAuthContractTest` now tests the actual servlet-filter behavior with `MockHttpServletRequest`/`MockHttpServletResponse`:
   - a must-change-password account receives HTTP 403 / `PASSWORD_CHANGE_REQUIRED` for a protected business API;
   - the same account can access the password-change authentication endpoint so it can recover its account.

2. `MultiEventCatalogIntegrationTest` imports a test-only `IntegrationPaymentGatewayConfiguration` that provides an ordered Razorpay implementation with deterministic behavior. No production credential or live provider is required.

3. The integration event fixture explicitly sets `RAZORPAY` rather than relying on a persistence default.

4. All release/build/runtime version manifests are synchronized to `1.9.54`.

5. A dedicated `ProductionHardeningV1_9_54ContractTest` protects the two test-harness changes and release-version consistency.

## Enterprise safety principle

No production payment-provider guard was weakened. Publishing an event in production still requires the selected real provider to be configured. The fake provider is compiled only from `src/test` and is imported only by the affected integration test.

## Static validation performed in this environment

- Neelastack stability baseline: PASS
- Shell syntax: PASS
- JSON parsing: PASS
- YAML parsing for Compose, GitHub Actions and application configuration: PASS
- Release version consistency: PASS
- Payment/recovery regression guards: PASS
- Forced-password-change behavior test structure: PASS
- Integration test provider isolation: PASS
- No generated `node_modules`, `target`, `.angular` or cache directories: PASS
- No `.env`, PEM or key artifacts in release tree: PASS

## CI qualification status

The supplied v1.9.53 run demonstrated that Java compilation was successful and that 173 of 175 tests passed before the two failures above. The corrected release is therefore designed to remove those exact failure causes without changing production business logic.

A fresh GitHub Actions `mvn -B -ntp clean verify` run remains the authoritative final qualification because this working environment cannot reproduce the complete Maven/Testcontainers execution stack.

## Final static gate result

All environment-independent release gates executed in the packaging environment passed. The Maven runner supplied with the CI log had already compiled 122 Java source files and entered the full Surefire test phase for v1.9.53; v1.9.54 does not alter production Java behavior, only test isolation and release metadata.
