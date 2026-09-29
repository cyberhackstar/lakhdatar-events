# Phase 7 — v1.4.8 Frontend Production Build Fix

The v1.4.7 CI run built the frontend successfully, but the final artifact validation found `localhost:8081` in the browser bundle.

Root cause: the `ng build` command was not explicitly tied to Angular's `production` configuration, while `environment.ts` contains the developer API URL.

Fix:
- `frontend/package.json`: `build` now runs `ng build --configuration production`.
- `frontend/angular.json`: build default configuration is `production`.
- Production environment continues to use `/api/v1`.

The CI leak check remains enabled.
