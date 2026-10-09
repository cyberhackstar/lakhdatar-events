# Neelastack Events — 2.0.33

## Enterprise dependency-resolution hardening

- Corrected Angular framework/tooling alignment to the published Angular 20.3.33 line.
- Kept Vitest at 3.2.7 to satisfy Angular Build 20.3.x peer requirements.
- Removed the stale Karma/Jasmine dependency graph from the frontend test stack.
- Added an isolated static, non-SSR Angular testing build configuration for the Vitest unit-test builder.
- Hardened dependency bootstrap to resolve the lockfile before destroying an existing install, never use force/legacy peer resolution, and fail closed on audit/contract failures.
- Pinned MCP SDK override to 1.31.0 and retained existing transitive dependency overrides.
- Corrected `.nvmrc` to the supported Node 22.19.0 baseline.
- Added runtime and full dependency audit gates.

## Release policy

This package is a release candidate, not production-certified until a networked environment regenerates `frontend/package-lock.json` from the authoritative manifest and passes the complete dependency, backend, frontend, browser, staging, and enterprise qualification gates.
