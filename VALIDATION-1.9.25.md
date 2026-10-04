# v1.9.25 Release Validation

## Scope
Repository-wide production-hardening validation of v1.9.24 with the v1.9.25 fixes applied.

## Changes verified
- Public sitemap shard authorization present in `SecurityConfig`.
- Sitemap page bounds enforced before paginated repository access.
- Event publish uses `findByPublicIdForUpdate`.
- Event update uses `findByIdForUpdate` on the parent event.
- New regression contract test added without changing existing test sources.
- Version metadata aligned to 1.9.25.
- No Flyway migration added; existing migration history remains intact.

## Environment limitation
The audit workspace does not provide Maven or Docker executables and does not guarantee package-network access. Therefore this report does not claim a live Maven build, container build, PostgreSQL migration run, or Oracle VM deployment. Those gates must be executed by CI/VM before production rollout.

## Packaging-sandbox gate results
- Platform baseline: PASS
- JSON: PASS (6 files)
- YAML: PASS (11 files)
- Shell syntax: PASS
- Java parse stage: PASS (no syntax diagnostics across production/test sources)
- Java structural sanity: PASS
- Migration sequence: PASS (V1-V24)
- Release version consistency: PASS (1.9.25)
- Existing test sources: PASS (0 modified/removed)
- Additive tests: 1 (`ReleaseRegressionContractTest`)
- Secret scan: PASS
- JavaScript/MJS syntax: PASS
- Repository stability baseline: PASS
- ZIP integrity: to be checked after packaging

## Mandatory external release gates
- Backend: `cd backend && mvn -B -ntp clean verify`
- Frontend: `cd frontend && npm ci --no-audit --no-fund && npm run build && npm test`
- Docker: multi-architecture build/startup plus NGINX smoke test
- Database: Flyway against the target PostgreSQL instance
- Production: Oracle VM + Cloudflare public smoke test
- External integrations: live Razorpay/Cashfree, Cloudinary and email delivery checks
