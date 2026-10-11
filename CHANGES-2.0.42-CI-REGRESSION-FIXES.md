# v2.0.42 — CI regression and release-package corrections

- Restored the safe, placeholder-only dotenv examples `.env.example`, `.env.staging.example`, and `infra/ha/.env.ha.example`. These are source templates, not deployment secrets, and the archive deliberately excludes populated `.env` files.
- Aligned `VERSION`, backend Maven version, frontend package and lockfile, E2E package and lockfile, the release manifest, and active application/Compose version fallbacks to `2.0.42`.
- Started from the v2.0.40 single-owner HSTS correction that fixed the public staging HSTS contract, then retained the v2.0.41 public-catalogue workload isolation and temporary k6 report-directory permission fix. Deployment and E2E source/business flows were not changed.
- Added an early baseline guard for the safe dotenv templates, package/release-version alignment, and retained both HSTS and load-test regression contracts so missing dotfiles and inconsistent manifests fail fast before the Maven suite.

## Known qualification boundary

The earlier ZAP run reported alert 10055 (`CSP: style-src unsafe-inline`). This release does not hide or waive that finding. A nonce/hash-based CSP change must be separately browser-tested with Angular SSR, inline component styling, and both payment providers before that finding can be marked resolved.
