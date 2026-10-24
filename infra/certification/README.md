# Enterprise release qualification and production certification

The **Enterprise Release Qualification** workflow validates the exact release SHA with source/security baselines, backend and frontend suites, fresh-and-upgrade Flyway migrations, browser E2E, staging load tests, and immutable-image DAST. On success it emits a `release-qualification-<SHA>` artifact. This manifest is explicitly **not** a production certificate.

The separate **Enterprise Production Certification** workflow requires the matching successful qualification artifact plus protected operational evidence for multi-failure-domain HA failover, PostgreSQL PITR/restore, payment-provider chaos/recovery, independent penetration/security review, real alert/paging validation, financial reconciliation, privacy/data-governance review, and privileged MFA recovery. Only this separate workflow can generate `enterprise-certification.json`.

Evidence IDs and PASS states must only be populated after the corresponding exercises are actually completed and reviewed. The certification gate is deliberately fail-closed and never fabricates certification. Missing configuration is reported as one aggregated checklist to prevent repeated runs for each missing variable.

The database qualification gate uses digest-pinned PostgreSQL and Flyway images supplied through protected/reviewed CI configuration (`POSTGRES_CERTIFICATION_IMAGE`, `FLYWAY_CERTIFICATION_IMAGE`).

## Single-node production readiness

For a one-VM production deployment, use the separate `Single-Node Production Readiness` workflow and `docs/SINGLE-NODE-PRODUCTION-READINESS.md`. It requires actual single-node recovery, off-host backup, restore/PITR, security, payment recovery, alerting, finance, privacy and MFA evidence plus measured RPO/RTO. Its artifact intentionally sets `enterpriseHACertificationGranted=false`; it cannot satisfy the HA promotion workflow. After reviewing the artifact and reports, an authorized reviewer approves the protected `production` environment. The production workflow automatically locates and verifies the successful readiness artifact for the exact release SHA; single-node topology and node count are constants in this deliberately single-node workflow, not GitHub Environment variables.
