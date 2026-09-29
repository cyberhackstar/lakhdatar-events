# Phase 6 — Angular SSR route-extraction stability fix (1.4.5)

## Problem

The Angular 20 browser and server dependency tree was aligned, but the production `ng build` failed during SSR route extraction with `NG0908`.

## Root cause

`zone.js` was present in `package.json` and the lockfile, but the standalone bootstrap entry points did not statically import Zone.js. The Angular application therefore reached SSR route extraction without Zone.js initialized.

## Fix

- `frontend/src/main.ts` now statically imports `zone.js`.
- `frontend/src/main.server.ts` now statically imports `zone.js/node`.
- Angular 20 / Node 22 dependency baseline remains unchanged.
- Release version is `1.4.5` across frontend, backend and root metadata.

## Validation

The supplied machine build reached bundle generation and failed only at SSR route extraction with `NG0908`; the fix specifically targets that stage. The release package also runs the static baseline verifier before packaging.
