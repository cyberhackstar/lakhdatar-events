# Neelastack Events — Enterprise Production Certification

## Certification boundary

v2.0.14 is the enterprise-certification-gated release. The repository enforces a protected promotion boundary: a production deployment must use the exact SHA stored in the protected `ENTERPRISE_CERTIFIED_SHA` variable, and enterprise HA promotion additionally requires the HA topology preflight and signed image/provenance verification.

This document intentionally does not claim that HA failover, PITR, penetration testing, payment chaos, real paging, or load qualification has occurred in this repository's local environment. Those are target-environment evidence requirements.

## Required evidence before certification

1. `mvn -B -ntp clean verify` passes on Java 21.
2. Angular `npm ci`, production build, and unit suite pass.
3. Playwright desktop, Android Chrome, iOS Safari and staff scanner qualification pass against disposable staging data.
4. Flyway V1–V37 passes on a fresh database and a V31 upgrade path.
5. Mandatory mixed load passes at the configured 1,000+ VU target, including checkout and check-in qualification.
6. Immutable-image DAST passes with no unresolved release-blocking findings.
7. HA failover across distinct application failure domains passes.
8. PostgreSQL backup/PITR restoration passes with measured RPO/RTO within policy.
9. Payment chaos/reconciliation scenarios pass for configured providers.
10. Privileged security review / penetration test passes.
11. Real production paging/alert routing passes.
12. Privileged MFA enablement/recovery is verified.
13. Finance reconciliation and privacy/data-governance reviews are signed off.

## Promotion

The certification workflow creates `certification-evidence/enterprise-certification.json` only after the protected `enterprise-certification` environment supplies the evidence identifiers and PASS attestations. The production HA workflow then refuses to deploy any SHA other than that protected certified SHA.

## Operating target

The enterprise production target is at least two application failure-domain nodes behind health-checked ingress, managed/HA PostgreSQL with independent PITR, managed/HA Redis, encrypted off-host backups, real alert paging, and immutable image deployment.
