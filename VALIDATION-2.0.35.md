# v2.0.35 validation

## Static validation completed in the build workspace
- Node syntax validation passed for `frontend/bootstrap-dependencies.mjs` and `frontend/doctor-dependencies.mjs`.
- JSON/YAML parsing validation passed for active frontend/build/deployment manifests.
- No `--force` or `--legacy-peer-deps` dependency-resolution flags are present in active frontend bootstrap scripts.
- Archive integrity validation passed for the release ZIP.

## Network-dependent validation
The sandbox cannot reach the npm registry, so a new `frontend/package-lock.json` was not fabricated. The release therefore intentionally uses the strict bootstrap workflow to generate the lockfile on a networked development/CI machine.

Required gate on the user's Windows host:

```powershell
cd frontend
node --version
npm --version
npm run bootstrap:dependencies
npm run doctor:dependencies
npm run verify:dependencies
npm audit --audit-level=high
npm audit --omit=dev --audit-level=high
npm run build
npm test
```

CI/staging release promotion must only proceed after the generated `package-lock.json` is reviewed and committed.
