# Enterprise certification evidence

The release qualification workflow proves application build/test, fresh-and-upgrade Flyway migrations, browser E2E, load, DAST, and source/security gates. Final enterprise certification additionally requires protected-environment evidence for multi-failure-domain HA failover, PostgreSQL PITR/restore, payment-provider chaos/recovery, independent penetration/security review, real alert/paging validation, financial reconciliation, and privacy/data-governance review.

Evidence IDs and PASS states must only be populated after the corresponding exercises are actually completed. `verify-enterprise-evidence.sh` is deliberately fail-closed and never fabricates certification.

The database gate uses digest-pinned PostgreSQL and Flyway images supplied through protected/reviewed CI configuration (`POSTGRES_CERTIFICATION_IMAGE`, `FLYWAY_CERTIFICATION_IMAGE`).
