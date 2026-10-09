# Neelastack Events — 2.0.34

## Enterprise dependency/bootstrap hardening

- Fixed the v2.0.33 bootstrap defect where the inner npm runner called `process.exit()`, bypassing the outer recovery path and hiding npm resolver diagnostics on Windows.
- The bootstrap now captures and prints npm stdout/stderr, preserves any existing lockfile until resolution succeeds, and restores it atomically on failure.
- Added a strict normal-install recovery path when `--package-lock-only` cannot reconcile the tree; both paths use npm's normal peer resolver and never use `--force` or `--legacy-peer-deps`.
- Kept the Angular framework/tooling baseline coherent at `20.3.33` and Vitest at `3.2.7`.
- Kept JSDOM at `29.1.1` and the fixed MCP TypeScript SDK override at `1.31.0`.
- Removed the stale Karma/Jasmine test dependency graph from the active frontend manifest.
- Updated dependency doctors/verifiers and the PowerShell lock-refresh utility to the same baseline.
- Kept Node development/CI baseline at `22.19.0`; the package engine also permits Node 24.x for the existing SSR runtime image.
- Kept full and runtime HIGH/CRITICAL dependency audits as mandatory release gates.

## Release policy

A valid `frontend/package-lock.json` must be generated on a networked release host and committed with the release. Production promotion remains blocked until the lock, backend, frontend, browser, staging, DAST and load qualification gates pass.
