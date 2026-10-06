# v2.0.21 Validation

## Baseline protection
- v2.0.20 retained as the functional baseline.
- No database migration added.
- No public API contract intentionally changed.
- Payment lifecycle behavior intentionally unchanged.

## Regression fix
- `PaymentProviderGuard` now passes all six configured payment capacity/circuit parameters to its state constructor.
- Focused `PaymentProviderGuardContractTest` added.

## Static validation
- VERSION consistency checked.
- JSON files checked.
- Shell scripts checked with `bash -n` where applicable.
- Java source inspected for the corrected constructor wiring.
- Release archive integrity checked after packaging.

## Not claimed locally
- Maven integration test execution requires the CI build environment.
- Cashfree/Razorpay sandbox E2E requires provider credentials.
- DAST, HA failover and PITR restore require staging infrastructure.
- High-concurrency k6 tests require the protected staging environment.
