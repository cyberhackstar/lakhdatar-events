# Neelastack Events — Enterprise Production Certification

## Certification boundary

Release qualification and enterprise production certification are separate workflows and separate decisions. **Enterprise Release Qualification** checks source/security baselines, backend/frontend, database migrations, browser E2E, staging load tests, and DAST for an exact SHA. Its successful artifact explicitly does not grant production certification.

**Enterprise Production Certification** separately verifies the matching release-qualification artifact and multi-node operational evidence in the protected `enterprise-certification` GitHub Environment. This is the enterprise-HA attestation path; its validator remains fail-closed for `PRODUCTION_TOPOLOGY=enterprise-ha` and at least two nodes. The single-node path is separately documented in `docs/SINGLE-NODE-PRODUCTION-READINESS.md`; it emits a distinct single-node readiness manifest and never grants HA certification. Single-node production promotion requires the exact reviewed SHA in the protected `SINGLE_NODE_CERTIFIED_SHA` variable; enterprise HA promotion continues to use `ENTERPRISE_CERTIFIED_SHA`, the HA topology preflight and signed image/provenance verification.

This document intentionally does not claim that HA failover, PITR, penetration testing, payment chaos, real paging, or load qualification has occurred in this repository's local environment. Those are target-environment evidence requirements.

## Required evidence before certification

1. `mvn -B -ntp clean verify` passes on Java 21.
2. Angular `npm ci`, production build, and unit suite pass.
3. Playwright desktop, Android Chrome, iOS Safari and staff scanner qualification pass against disposable staging data.
4. Flyway V1–V41 passes on a fresh database and a V31 upgrade path.
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

The Enterprise Production Certification workflow creates `certification-evidence/enterprise-certification.json` only after it verifies the successful qualification artifact for the same SHA and the protected `enterprise-certification` environment supplies real evidence identifiers, measured RPO/RTO, and PASS attestations. The single-node production workflow refuses to deploy any SHA other than the protected `SINGLE_NODE_CERTIFIED_SHA`; the HA promotion workflow continues to require `ENTERPRISE_CERTIFIED_SHA`.

## Operating target

The enterprise production target is at least two application failure-domain nodes behind health-checked ingress, managed/HA PostgreSQL with independent PITR, managed/HA Redis, encrypted off-host backups, real alert paging, and immutable image deployment.
