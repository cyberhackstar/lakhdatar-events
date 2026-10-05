# v2.0.8 Validation

- Frontend dependency verifier: PASS
- `npm install --package-lock-only --ignore-scripts --offline`: PASS
- Node syntax check for dependency verifier: PASS
- Lockfile contains no `hasown-2.0.5.tgz`, `inherits-2.0.5.tgz`, `http-errors-2.0.2.tgz`, or `void-elements-2.0.2.tgz` references.
- `hasown` resolves to 2.0.4 with the published registry integrity.
- `is-core-module` 2.17.0 dependency range is `hasown ^2.0.4`.
- `http-errors` 2.0.1 dependency range is `inherits ~2.0.4`.
- Application backend/frontend version synchronized to 2.0.8.

Runtime CI gates such as full Maven tests and complete networked npm install remain CI-authoritative.
