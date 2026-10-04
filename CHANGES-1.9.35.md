# v1.9.35 — Build compilation correction

## Production fixes

- Corrected the Angular `ApiService.queryParams()` type signature so strongly typed query models such as `EventQuery` are accepted by the helper without requiring an index signature.
- Preserved the v1.9.34 runtime behavior: Cashfree checkout handling, scanner camera reuse, optional-query sanitization, and Cloudflare CSP hardening remain unchanged.
- Added a frontend regression contract covering typed `EventQuery` parameters and preservation of valid `false` / `0` values.
- No backend API contract, payment verification rule, ticket credential rule, check-in authorization logic, or Flyway migration was changed.

## Validation

- The reported CI compilation error `TS2345` at `frontend/src/app/core/api/api.service.ts:49` is addressed by the generic helper signature.
- Release baseline validation passes statically.
- Full Angular/Maven builds must still execute in CI because this packaging environment cannot reach the npm registry and does not have Maven installed.
