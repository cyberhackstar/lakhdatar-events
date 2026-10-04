# Neelastack Events v1.9.42

## CI/test correction

- Fixed `EnterpriseScaleContractTest` so it validates the supported Angular test bootstrap via `tsconfig.spec.json` and `src/test-polyfills.ts`.
- Removed the stale expectation that Angular `@angular/build:unit-test` accepts an unsupported `polyfills` option in `angular.json`.
- Preserved the existing Zone.js bootstrap required by the Angular test suite.
- No production runtime, payment, booking, ticket, scanner, finance, database, or deployment behavior was changed.

## Release posture

This patch is intended to clear the remaining v1.9.41 backend contract-test failure without reintroducing the earlier Angular test-builder schema failure.
