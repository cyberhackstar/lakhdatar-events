# v1.9.36 Validation

## Source-level checks

- Zone.js test bootstrap is explicit in all frontend spec files.
- VERSION, Maven and npm package versions are aligned to 1.9.36.
- v1.9.35 query-parameter type correction remains present.
- v1.9.34 payment/scanner reliability changes remain present.

## Required CI gates

Run in GitHub Actions on the release commit:

`cd frontend && npm ci --no-audit --no-fund && npm run verify:baseline && npm run build && npm test`

Then run the existing backend, multi-architecture Docker, edge startup, image security scan, SSR smoke, and Cloudflare public smoke gates.

This package was produced without silently claiming external GitHub/npm/Maven execution from the packaging sandbox.
