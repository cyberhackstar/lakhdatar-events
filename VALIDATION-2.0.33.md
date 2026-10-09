# Validation — 2.0.33

## Source-level validation

- Release version consistency: PASS
- Active Angular dependency versions aligned to 20.3.33: PASS
- Vitest 3.2.7 / JSDOM 29.1.1 manifest alignment: PASS
- Karma/Jasmine direct dependency removal: PASS
- Vitest test builder configuration: PASS
- Isolated static/non-SSR test build target: PASS
- Node baseline `.nvmrc`: 22.19.0

## Networked validation required

This environment cannot reach the npm registry and therefore cannot truthfully generate or certify a fresh frontend `package-lock.json`. The following must execute on the developer/CI network before production certification:

```text
npm run bootstrap:dependencies
npm run doctor:dependencies
npm run verify:dependencies
npm audit --audit-level=high
npm audit --omit=dev --audit-level=high
npm run build
npm test
```

Then execute the enterprise Chromium suite and staging qualification gates.
