# Validation — v2.0.29

## Security remediation included

- Migrated Angular unit testing from Karma/Jasmine to Angular-supported Vitest.
- Removed the deprecated Karma/Jasmine package chain responsible for the reported local dependency warnings.
- Added a full high-severity frontend dependency audit in CI in addition to the production-runtime audit.
- Added fail-closed lock verification that requires Vitest/jsdom and rejects the removed Karma/Jasmine packages.
- Preserved all v2.0.28 backend reliability fixes, including ticket PDF rendering, ECS structured logging, and webhook retry timestamp handling.

## Source/static validation

- Package JSON parses successfully.
- Angular workspace JSON parses successfully.
- TypeScript test config parses successfully.
- Vitest imports are present in all three migrated specs.
- Jasmine/Karma imports are removed from active specs/configuration.
- Release metadata is aligned to 2.0.29.

## Required networked validation

The frontend lockfile must be regenerated on a network-enabled runner because this execution environment cannot access the npm registry. This is intentionally fail-closed: the supplied lockfile still contains the previous Karma tree and `tools/verify-frontend-lock.mjs` will reject it until the lock is regenerated.

Run from `frontend/`:

```powershell
npm install --package-lock-only --ignore-scripts --no-fund
npm ci --ignore-scripts --no-fund
npm audit --audit-level=high
npm run verify:dependencies
npm run build
npm test
```

Then run the local Docker stack and the non-mutating Chromium E2E suite. Production promotion remains blocked until staging enterprise qualification is green against the exact release SHA.
