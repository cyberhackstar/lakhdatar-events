# v2.0.50 — Separate single-node production readiness

- Added a protected, manual `Single-Node Production Readiness` workflow tied to the exact successful release-qualification artifact SHA and run ID.
- Added fail-closed evidence validation for single-node recovery, offsite backups, database restore/PITR, security assessment, payment recovery, alerting, reconciliation, privacy and privileged MFA, with measured RPO/RTO bounds.
- The readiness manifest expressly grants no enterprise HA certification. Existing enterprise HA certification and HA promotion validators remain unchanged.
- Updated the single-node Production workflow to require `PRODUCTION_TOPOLOGY=single-node`, `HA_NODE_COUNT=1`, explicit break-glass acknowledgement and exact `SINGLE_NODE_CERTIFIED_SHA`; signing/provenance and post-deployment smoke tests remain in place.
- Bumped active application/package coordinates to 2.0.50.
