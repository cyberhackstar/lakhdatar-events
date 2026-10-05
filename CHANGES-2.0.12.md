# v2.0.12 — Enterprise production certification hardening

## Application security and correctness
- Privileged MFA reset now revokes all active refresh-token sessions for the target user.
- Production boot now fails closed unless `MFA_REQUIRED_FOR_PRIVILEGED=true`.
- Webhook stale-processing recovery is provider-scoped for Razorpay and Cashfree.
- Cashfree recovery now resets stale claims with the same bounded retry/dead-letter policy as Razorpay.
- Removed the stray temporary Java source file from the release tree.
- Stateful business E2E mutations now run serially with retries disabled so a test retry cannot consume the same checkout/QR state twice.

## Enterprise certification and delivery
- Added mandatory fresh-database and V31-to-latest Flyway qualification for release certification.
- Added retained DAST, load-test, and database evidence artifacts to the certification pipeline.
- Added HA environment fail-closed validation, GHCR authentication on both HA nodes, and a dedicated HA rollback workflow.
- Added fail-closed protected-environment enterprise evidence validation.
- Certification requires reviewed HA failover, PITR, payment-chaos, penetration/security, alerting, finance reconciliation, and privacy evidence IDs.
- Certification requires measured RPO/RTO values and at least two application failure-domain nodes.
- Production promotion verifies Cosign signatures and GitHub artifact provenance for the exact immutable image SHA before deployment.
- Added a dedicated enterprise HA promotion workflow and HA-node deploy/rollback scripts.
- Added repository security policy, Dependabot maintenance, CODEOWNERS guidance, and repository governance controls.
- Added v2.0.12 contract tests for the new release invariants.

## Certification model
This release is the **enterprise production certification-gated release**. The source tree and gates are hardened to prevent promotion without the required evidence. Actual HA/PITR/load/DAST/penetration evidence must be generated in the target staging/production-equivalent environment; this repository does not fabricate those results.

- Certification evidence now records the certification timestamp and explicitly requires privileged-MFA evidence.

## Final certification release controls
- Added an explicit enterprise-production certification record and release manifest.
- Added mandatory provider-authenticated GHCR pulls for HA application nodes and signed-image verification on the release runner.
- Added provider-safe HA rollback workflow and protected HA environment validation.
- Added digest-pinned PostgreSQL/Flyway CI qualification, with fresh and V31 upgrade paths plus retained evidence.
- Added cross-device staff scanner browser qualification and retained DAST/load/database evidence artifacts.
