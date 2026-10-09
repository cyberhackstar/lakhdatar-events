# Neelastack Events 2.0.31

## Frontend dependency-resolution correction

### Root cause fixed
The 2.0.30 candidate carried a stale package-lock.json whose Angular package records were older than package.json. npm therefore resolved an inconsistent graph and failed with ERESOLVE (`@angular/common@undefined`).

### Enterprise remediation
- Keep the Angular v20 LTS line coherent at 20.3.39.
- Keep Vitest at 3.2.7, compatible with the Angular build test runner used by this release line.
- Keep JSDOM at 29.1.1 for headless DOM tests.
- Pin the transitive `@modelcontextprotocol/sdk` override to the first fixed 1.x release, 1.31.0.
- Remove the stale frontend package-lock.json rather than shipping a lockfile known to be inconsistent with package.json.
- Add `.npmrc` with `legacy-peer-deps=false` and `engine-strict=true`; dependency bypasses are not accepted.
- Add `npm run bootstrap:dependencies`, which creates the authoritative lockfile from package.json, verifies clean `npm ci`, runs the dependency verifier and executes the high-severity npm audit.
- Add a fail-closed verifier message when the lockfile is absent.

### Production gate
This release is not production-certified until the networked lock refresh produces a committed package-lock.json and all dependency, build, unit, browser E2E, staging qualification, DAST/load, and release-evidence gates pass.
