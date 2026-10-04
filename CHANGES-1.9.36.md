# v1.9.36 — frontend test-runtime and release consistency correction

## Production release fixes

- Fixed the CI frontend unit-test failure `NG0908: In this configuration Angular requires Zone.js`.
- Explicitly loads `zone.js` and `zone.js/testing` before Angular test imports in every frontend spec.
- Preserved the v1.9.34 payment/scanner/admin-query runtime hardening and the v1.9.35 `ApiService.queryParams()` type correction.
- Aligned release metadata to v1.9.36 across VERSION, backend Maven manifest, frontend package manifests, runtime version defaults and HA examples.
- No backend transaction semantics, payment verification, ticket credential rules, check-in authorization, database migrations, or public API contracts were changed.

## CI issue addressed

The v1.9.35 CI build completed successfully, then `npm test` executed 7 suites with 3 failures. All three failures had the same root cause: the TestBed suites started without Zone.js and failed with `NG0908`; the subsequent `Cannot read properties of undefined (reading 'verify')` was a cascading failure from the same setup problem.
