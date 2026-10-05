# v2.0.6 Validation Record

## Source-driven CI failures addressed

The supplied CI evidence showed two independent blockers:

1. Backend: 188 tests ran and exactly one failed in `EnterpriseLoggingContractTest.logApiIsAdminOnlyNoStoreAndBounded` because the logging service still contained a source string matching `response.body`. The service was corrected and the contract updated to guard the safe implementation.
2. Frontend/security: `npm ci` stopped with HTTP 404 while fetching `inherits-2.0.5.tgz`. The lockfile now resolves `inherits` to the published 2.0.4 package and a root npm override pins `inherits` for `http-errors@2.0.1`. The deprecated direct Angular animation/dynamic-platform packages were also removed and the unused animations provider was removed.

Gitleaks reported no leaks in the supplied security job; its job failed at the same dependency-install stage before the runtime dependency audit could run.

## Validation executed in this release workspace

- `node --check tools/verify-frontend-lock.mjs` — PASS
- `node --check tools/verify-platform-baseline.mjs` — PASS
- `frontend/node ../tools/verify-platform-baseline.mjs` — PASS
- `frontend/node ../tools/verify-frontend-lock.mjs` — PASS
- `npm ci --dry-run --ignore-scripts --no-audit --no-fund` — PASS; npm resolved `inherits 2.0.4` and `http-errors 2.0.1` with 656 packages
- `npm ci --omit=dev --ignore-scripts --no-audit --no-fund --dry-run` — PASS
- Primary NGINX configuration syntax check — PASS
- HA NGINX reference configuration syntax check — PASS
- Primary edge routing smoke test with stub upstreams — PASS for monitor root redirect, monitor/business separation, SRE API allowance, public API denial, ticket denial and authentication UI availability
- Java source brace/placeholder structural validation — PASS
- JSON parsing for `frontend/package.json` and `frontend/package-lock.json` — PASS
- Bash syntax validation for repository shell scripts — PASS
- JavaScript/MJS syntax validation — PASS
- Final release artifact hygiene scan — PASS; no `.env`, private key, certificate, JAR/WAR, target/dist/node_modules or nested release ZIP is packaged

## Important runtime gates

This workspace does not contain Maven and full external-service infrastructure, so the following are intentionally not represented as executed here:

- `mvn -B -ntp clean verify`
- full `npm ci && npm run build`
- production `npm audit --omit=dev --audit-level=high`
- Docker multi-architecture build and startup
- deployed Cloudflare Access policy validation
- Razorpay/Cashfree live sandbox transactions
- PostgreSQL PITR restore drill and Redis failover drill

These remain required CI/staging/production release gates. The package contains the fixes required for the observed blockers and preserves fail-closed deployment behavior.
