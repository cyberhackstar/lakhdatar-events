# Validation — v2.0.30

## Release gate status

- Backend v2.0.28 reliability fixes are retained.
- Backend Maven suite from v2.0.28: 237 tests, 0 failures, 0 errors.
- Frontend v2.0.28 baseline: production SSR build and 12/12 unit tests passed.
- v2.0.29 dependency migration exposed an npm peer conflict because Vitest 4.x does not satisfy Angular build 20.3.x's optional Vitest peer.
- v2.0.30 pins Vitest 3.2.7 and jsdom 29.1.1, pins Angular 20.3.39, and pins the transitive MCP SDK to 1.31.0.

## Required networked validation

The lockfile must be regenerated from `frontend/package.json` on a network-enabled workstation/CI runner. Do not use `--force` or `--legacy-peer-deps`.

```powershell
cd frontend
npm install --package-lock-only --ignore-scripts --no-fund
npm ci --ignore-scripts --no-fund
npm audit --audit-level=high
npm run verify:dependencies
npm run build
npm test
```

The release gate is fail-closed until the regenerated lockfile contains Vitest 3.2.7, jsdom 29.1.1, no Karma/Jasmine packages, and `@modelcontextprotocol/sdk` 1.31.0.

After frontend validation: rebuild the local Docker stack, run the full non-mutating Chromium suite, deploy the exact candidate to staging, execute enterprise release qualification, then consider production promotion.
