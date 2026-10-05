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

### CI failure fixes
- Dependabot PR #19 was failing in `verify:baseline` because the PR intentionally upgrades `express` from the controlled `^4.22.2` baseline to `^5.2.1` and `@types/express` to the Express 5 type line. The validator now allows only those two explicitly approved Express baselines.
- Main Docker CI was failing its edge regression because NGINX converted the relative `Location: /monitor` into an absolute origin URL containing the internal `:8080` port. `absolute_redirect off;` is now enforced in both the primary and HA edge configurations.
- No broad test relaxation was added: the remaining frontend build/tests and the edge regression still run after these baseline gates.
