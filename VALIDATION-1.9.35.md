# v1.9.35 — Validation record

## Reported CI failure corrected

The v1.9.34 frontend CI failed during `ng build --configuration production` with:

`TS2345: Argument of type 'EventQuery' is not assignable to parameter of type 'Record<string, unknown>'.`

The failure originated from passing the typed `EventQuery` model into the query-parameter sanitizer. The sanitizer is now generic over `T extends object`, while still filtering `undefined`, `null`, and blank values before creating `HttpParams`.

## Packaging validation

- Baseline source contract: PASS.
- TypeScript source edit is syntactically valid.
- Frontend CI remains the authoritative build gate. A local production Angular build could not be executed in this packaging sandbox because npm registry DNS resolution returned `EAI_AGAIN`; Maven is not installed in the sandbox.

## Compatibility

The application behavior from v1.9.34 is retained. The only functional code change in v1.9.35 is the compile-safe typing of the existing query-parameter sanitizer plus its regression test.
