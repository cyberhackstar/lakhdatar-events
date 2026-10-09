# v2.0.30 — Enterprise Dependency Resolution & Security Hardening

- Corrected the v2.0.29 Vitest peer dependency conflict by pinning Vitest 3.2.7, compatible with Angular build 20.3.x.
- Pinned Angular framework/build/CLI/SSR/compiler-cli packages to 20.3.39 for a deterministic v20 LTS dependency baseline.
- Added an npm override for `@modelcontextprotocol/sdk` 1.31.0 to address the October 6, 2026 high-severity OAuth credential routing advisory affecting versions before 1.31.0. citeturn523468search0
- Updated frontend lock verification to enforce exact security/tooling versions.
- Preserved the v2.0.28 backend PDF, ECS logging, and webhook recovery fixes.
- Production promotion remains fail-closed until the network-generated lockfile, npm audit, frontend build/tests, Chromium E2E, and staging qualification are green.
