# Single-Node Production Readiness

This path is for the current deployment architecture: one production VM (`PRODUCTION_TOPOLOGY=single-node`, `HA_NODE_COUNT=1`). It checks operational resilience and security evidence for a single node; it **does not** certify high availability, provide node failover, or imply there is no downtime during VM failure. The existing `Enterprise Production Certification` and `Enterprise Production HA Promotion` flows remain fail-closed and must only be used for an actual multi-node HA topology.

## Protected environment

Create a protected GitHub Environment named `single-node-production-readiness` (require reviewer approval). Configure these as **Environment Variables** because the workflow uses `${{ vars.NAME }}`. Populate values only after the corresponding exercise/review completed successfully and a retained report or immutable evidence record exists. Do not use placeholders or attest to unperformed drills.

- `PRODUCTION_TOPOLOGY=single-node`
- `HA_NODE_COUNT=1`
- `RPO_SECONDS` and `RTO_SECONDS`: measured values from the real backup/restore or recovery drill
- `MAX_RPO_SECONDS` (optional, defaults to `300`)
- `MAX_RTO_SECONDS` (optional, defaults to `1800`)
- `SINGLE_NODE_RECOVERY_STATUS=PASS` and `SINGLE_NODE_RECOVERY_EVIDENCE_ID`: restart/recovery drill on the single production node or representative environment
- `OFFSITE_BACKUP_STATUS=PASS` and `OFFSITE_BACKUP_EVIDENCE_ID`: backup stored outside the production VM and retention/access reviewed
- `PITR_STATUS=PASS` and `PITR_EVIDENCE_ID`: successful database point-in-time/restore drill, or documented restore method actually exercised
- `PEN_TEST_STATUS=PASS` and `PEN_TEST_EVIDENCE_ID`: current security assessment and reviewed findings
- `PAYMENT_CHAOS_STATUS=PASS` and `PAYMENT_CHAOS_EVIDENCE_ID`: provider retry/idempotency/failure recovery tests
- `ALERTING_STATUS=PASS` and `ALERTING_EVIDENCE_ID`: tested alerts for service failure, DB failure and low disk space
- `FINANCE_RECONCILIATION_STATUS=PASS` and `FINANCE_RECONCILIATION_EVIDENCE_ID`
- `PRIVACY_REVIEW_STATUS=PASS` and `PRIVACY_REVIEW_EVIDENCE_ID`
- `MFA_STATUS=PASS` and `MFA_EVIDENCE_ID`: privileged MFA enforcement and recovery drill

Evidence IDs must be stable references (6–256 characters; letters, digits, `.`, `_`, `:`, `/`, and `-` are accepted). The identifier is not proof on its own; retain and review the referenced record. Measured RPO/RTO must not exceed configured maximums.

## Release sequence

1. Run **Enterprise Release Qualification** for the exact release SHA and record the successful run ID. This validates the source/backend/frontend/database/browser/load/DAST gates; it is not operational certification.
2. Complete the operational exercises and configure their real evidence variables in `single-node-production-readiness`.
3. Run **Single-Node Production Readiness** with that exact SHA and qualification run ID. It verifies the matching qualification artifact and creates `single-node-production-readiness-<SHA>` only if all evidence passes. The manifest explicitly sets `enterpriseHACertificationGranted=false`.
4. Review the readiness manifest and supporting records. The successful run publishes `single-node-production-readiness-<SHA>` and its JSON manifest.
5. Run **Production** with the same release SHA and type `SINGLE-NODE-BREAK-GLASS` into the confirmation field. The production workflow locates the successful protected readiness artifact for that exact SHA, verifies its status/topology/evidence identifiers and rejects attestations older than 90 days. There is no manually maintained production topology/node-count/SHA variable. GitHub's protected `production` environment approval remains mandatory, and image signature/provenance plus smoke checks remain required.

The production workflow does not accept the HA certified SHA as a substitute for single-node readiness. Conversely, the single-node workflow cannot produce the enterprise HA certification artifact. These are distinct attestations. Set realistic RPO/RTO targets after measuring them; a single VM has a single-node failure domain and may remain unavailable until restored.
