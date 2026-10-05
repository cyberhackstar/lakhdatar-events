# v2.0.7 Validation Record

## Source-driven CI blocker

The provided CI logs show the frontend job and the security job both stop at `npm run verify:dependencies` with `lockfile root override missing`. Gitleaks succeeds before the security job reaches that failure.

The v2.0.7 fix changes the verification contract to the npm-supported model: root `package.json` owns `overrides`, while the lockfile is validated by its resolved package entries. The lockfile resolves `node_modules/inherits` to 2.0.4 and contains no `inherits-2.0.5.tgz` reference. npm documents `overrides` as root `package.json` policy.

## Workspace validation

- `node --check tools/verify-frontend-lock.mjs` — PASS
- `node tools/verify-frontend-lock.mjs` — PASS
- `node tools/verify-platform-baseline.mjs` — PASS
- `npm install --package-lock-only --ignore-scripts --no-audit --no-fund` — PASS
- `npm ci --dry-run --ignore-scripts --no-audit --no-fund` — required CI-equivalent dependency validation is included as a release gate; full networked install could not be certified in this workspace because the container transport timed out during package download.
- NGINX primary and HA reference syntax/static checks — retained PASS from v2.0.6 baseline
- JavaScript/MJS syntax — PASS
- Bash syntax — PASS
- JSON/YAML parsing — PASS
- release version consistency — PASS (2.0.7)
- artifact hygiene — PASS

## Runtime gates not executed here

- `mvn -B -ntp clean verify`
- full `npm ci && npm run build`
- production `npm audit --omit=dev --audit-level=high`
- Docker multi-architecture build/startup
- Cloudflare Access runtime validation
- Razorpay/Cashfree sandbox transactions
- PostgreSQL PITR/restore drill and Redis failover drill
