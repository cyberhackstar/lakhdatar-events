# Frontend Dependency Remediation — 2.0.30

## Security objective

The frontend test toolchain has been migrated from the deprecated Karma/Jasmine stack to Angular's supported Vitest runner. This removes the old Karma transitive dependency chain that was responsible for the local install warnings involving `inflight`, `rimraf`, `glob`, and `braces`.

The production SSR container already installs runtime dependencies with `npm ci --omit=dev`; test-only dependencies are therefore excluded from the runtime image. CI now performs two explicit gates:

1. full frontend dependency audit, including the build/test supply chain;
2. production-runtime dependency audit with `--omit=dev`.

## Controlled changes

- Removed Karma, Karma plugins, Jasmine runtime, and `@types/jasmine`.
- Added Vitest `3.2.7` and jsdom `29.1.1`.
- Migrated the Angular unit-test target to `runner: vitest`.
- Reworked the three existing specs to use Vitest imports/matchers.
- Removed the obsolete `zone.js/testing` test polyfill.
- Added a dedicated high-severity dependency audit script.
- Added lockfile verification rules forbidding the removed Karma/Jasmine dependency chain.

## Lockfile regeneration

The release source intentionally does **not** claim a regenerated frontend lockfile from an offline build environment. Regenerate it from a network-enabled workstation/CI runner before merging:

```powershell
cd frontend
npm install --package-lock-only --ignore-scripts --no-fund
npm ci --ignore-scripts --no-fund
npm audit --audit-level=high
npm run build
npm test
```

The resulting `frontend/package-lock.json` is a required release artifact. `tools/verify-frontend-lock.mjs` will fail closed until it reflects the Vitest migration and contains no Karma/Jasmine packages.

## Peer dependency correction

`@angular/build` 20.3.x declares an optional Vitest peer in the `^3.1.1` line. The previous 2.0.29 candidate incorrectly selected Vitest 4.x, causing npm `ERESOLVE`. v2.0.30 pins Vitest 3.2.7 so the Angular test runner and npm peer resolver agree without `--force` or `--legacy-peer-deps`.

## MCP security override

Angular CLI currently brings in `@modelcontextprotocol/sdk`. The October 6, 2026 high-severity advisory affects SDK 1.x versions before 1.31.0; this release pins the transitive SDK to 1.31.0 via an npm override. citeturn523468search0
