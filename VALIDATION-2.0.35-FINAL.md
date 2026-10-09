# Neelastack Events v2.0.35 — Final Qualification Record

## Release state
- Release version: `2.0.35`
- Frontend Angular line: `20.3.33`
- Frontend SSR security patch: `@angular/ssr 20.3.39`
- Vitest: `3.2.7`
- Tinypool override: `2.2.0`
- Node engine: `>=22.19.0 <23 || >=24.0.0 <25`
- CI Node baseline: `.nvmrc` = `22.19.0`
- Java baseline: `21`

## Corrections included
1. Integrated the Windows-safe npm bootstrap/doctor implementation from the supplied v2.0.35 CI/audit patch.
2. Added `frontend/src/test-polyfills.ts` with `zone.js` and `zone.js/testing`, and explicitly included it in `frontend/tsconfig.spec.json`.
3. Kept Angular SSR at `20.3.39`, which is beyond the currently reported affected range for GHSA-7g7c-h8rr-7p6q.
4. Kept `tinypool` forced to `2.2.0` to remove the reported critical advisory path in the Vitest 3 dependency tree.
5. Updated the root `@types/node` development pin from `22.9.0` to `22.20.5`, with matching `undici-types 6.21.0` lock entries. This removes the Vite optional-peer mismatch seen in the supplied bootstrap log.
6. Synchronized E2E `package-lock.json` with release `2.0.35`.
7. Corrected active HA example defaults from `2.0.34` to `2.0.35`.
8. Corrected `ProductionHardeningV206ContractTest` so it validates security tarball absence without requiring an unused dependency (`void-elements`) to exist in the current lock tree.

## Qualification performed in this build environment
- `node tools/verify-frontend-lock.mjs` — PASS
- `node tools/verify-platform-baseline.mjs` — PASS
- `npm ci --dry-run --include=dev --ignore-scripts --no-audit --no-fund --offline --engine-strict=false` — PASS (lockfile resolves cleanly; no ERESOLVE peer warning)
- JSON syntax validation — PASS (13 repository JSON files)
- Shell syntax validation — PASS
- JavaScript/MJS syntax validation — PASS
- Release/version contract simulation — PASS

## Previously observed application tests
The supplied v2.0.35 Windows log records frontend dependency verification PASS, production runtime audit `0 vulnerabilities`, 12/12 frontend unit tests passing, and a successful production Angular build. The supplied backend run records 237 tests with exactly three release-contract blockers; this release addresses those three blockers.

## Environment limitation
A fresh Maven `clean verify` was not executable in this packaging environment because Maven is not installed here. Therefore this record does not claim a new 237-test Maven execution; the backend changes were validated against the exact failing contract conditions reported in the supplied build log.

## Security note
The remaining moderate finding reported by the supplied frontend audit is the dev-only `@vitest/mocker` / Vitest 3 path-traversal advisory. The current fixed advisory release is Vitest 4.1.11 / 5.x, while this repository's Angular 20.3.33 build contract intentionally pins Vitest 3.2.7. The production/runtime audit reported `0 vulnerabilities`, and the CI policy continues to fail on HIGH/CRITICAL findings.
