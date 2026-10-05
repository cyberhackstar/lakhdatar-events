# v2.0.8 — Dependency Lock Integrity Fix

## Root cause
CI v2.0.7 passed the custom dependency verifier but `npm ci` still attempted to fetch `hasown-2.0.5.tgz`. The lockfile contained a fabricated/non-published resolution and an incorrect `is-core-module` dependency range.

## Fix
- Pin transitive `hasown` to the published `2.0.4` release via root `package.json` overrides.
- Correct `node_modules/hasown` lock entry to the published 2.0.4 tarball and integrity.
- Correct `is-core-module@2.17.0` dependency metadata to `hasown: ^2.0.4`.
- Correct `http-errors@2.0.1` dependency metadata to `inherits: ~2.0.4`.
- Retain safe `inherits: 2.0.4` override.
- Extend the frontend lock verifier to reject stale invalid tarball references for hasown/inherits/http-errors/void-elements.
- Synchronize application/release contract version to 2.0.8.

## Evidence
- npm currently publishes `hasown` 2.0.4; 2.0.5 is not a published release.
- npm documents root `package.json` as the authoritative location for `overrides`, while `package-lock.json` records the exact resolved tree.

## CI regression hardening (post-release maintenance)
- The frontend stability baseline now explicitly supports the controlled Express 5.2.1 / `@types/express` 5.0.6 Dependabot upgrade while retaining the existing Express 4.22.x baseline.
- The edge and HA NGINX configs disable `absolute_redirect`, preventing NGINX from rewriting the relative monitor redirect to an internal `http://monitor.neelastack.com:8080/...` URL.
- The existing CI regression remains strict: the monitor hostname root must return a relative `/monitor` location.
