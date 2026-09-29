# Phase 5 validation — release 1.4.2

## Static validation completed

- JSON (`frontend/package.json`, `frontend/angular.json`): parsed successfully.
- XML (`backend/pom.xml`): parsed successfully.
- TypeScript source parsing: 0 syntax diagnostics across `frontend/src`.
- Shell syntax (`infra/deploy/deploy.sh`, backup/restore scripts): passed `bash -n`.
- Repository scan: no production deployment path uses `/opt`; the remaining `/opt/` text is only the negative assertion inside a regression test.
- Repository scan: no stale Angular 21 or Spring Boot 3.5 dependency declaration remains outside the historical lockfile/older release-history notes.
- Angular production builder is `@angular/build:application`.
- The exact supplied Angular compiler errors in `EventEditorComponent` are patched in 1.4.2.
- iOS input zoom guard is present and editable controls are 16px or larger.
- SSR Docker runtime installs production dependencies instead of assuming they are present in the copied build output.
- Event-manager authorization and complimentary-ticket provenance regression contracts are present.
- EVENT_MANAGER is excluded from financial refund approval.

## Dependency-resolution limitation

The release bundles the Angular 20 `frontend/package-lock.json` from the successful Node 22/npm 10 dependency resolution supplied with this build. The sandbox validates the lockfile metadata and baseline, but does not have Maven installed and does not contain a complete Angular dependency cache, so the full backend/frontend build must still run in CI or on the development machine.

Use:

```powershell
./scripts/refresh-frontend-lock.ps1
```

That script performs `npm install`, baseline validation, `npm ci`, and the production build using Node 22.x. Keep the committed lockfile and keep Docker/CI on `npm ci`.

Backend dependency resolution and the full Testcontainers build still need to run in CI or on the development machine with Maven/Docker available.
