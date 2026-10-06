# Validation — Neelastack Events v2.0.15

## Source-level checks
- Java source balance: required checks pass.
- YAML/JSON/XML/shell/JavaScript syntax checks: pass.
- Active release metadata aligned to 2.0.15.
- Flyway V38 added for refresh-token MFA session proof.
- HA verification aligned to the deployed two-node topology.

## Known CI regressions addressed
The supplied v2.0.14 CI run reported exactly two compile errors: missing `log` in `EventManagementService` and missing `distributedLocks` in `MfaService`. Those fields are now explicit in source.

## Runtime qualification
Not claimed here unless produced by the actual protected GitHub Actions/staging environments: Maven `clean verify`, Angular production build/tests, Playwright browser E2E, payment sandbox tests, 1,000+ mixed load, DAST, HA failover, and PITR restore.

## Additional hardening
- Frontend stale-session regression covered for MFA-pending login responses.
- HA API/worker JWT issuer and audience linkage verified as required environment inputs.
