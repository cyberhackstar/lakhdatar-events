# Validation 2.0.31

Static validation completed for the dependency resolution correction.

The previous stale lockfile is intentionally not shipped. This avoids repeating the ERESOLVE failure caused by a package-lock graph that did not match package.json. On a networked developer/CI runner, execute `npm run bootstrap:dependencies`; it regenerates the lockfile, performs a clean `npm ci`, verifies the dependency graph, and runs `npm audit --audit-level=high`.
