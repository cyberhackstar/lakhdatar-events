# Single-Node Production Readiness

This path is for the current deployment architecture: one production VM (`PRODUCTION_TOPOLOGY=single-node`, `HA_NODE_COUNT=1`). It checks operational resilience and security evidence for a single node; it **does not** certify high availability, provide node failover, or imply there is no downtime during VM failure. The existing `Enterprise Production Certification` and `Enterprise Production HA Promotion` flows remain fail-closed and must only be used for an actual multi-node HA topology.

## Evidence source and protected approval

The readiness gate no longer consumes eighteen manually maintained control-status/evidence-ID variables. It reads immutable, machine-readable records from Cloudflare R2 and fails closed if the newest record for any control is failed, stale, bound to another release where release binding is required, or has a missing/mismatched checksum.

Each readiness collector creates a human-readable report plus a checksummed `.result.json` record for **PASS and FAIL**. R2 stores both artifacts and their `.sha256` sidecars under `production/readiness-evidence/`. The aggregator verifies the result bytes, the referenced source report/bundle bytes, the evidence age, release identity, reviewer requirements, and the reviewed RPO/RTO scope. A failed re-run supersedes an older PASS based on `finishedAtUtc`, not upload time.

Create a dedicated R2 bucket named `neelastack-readiness-evidence` for machine-readable readiness evidence and its reports. Do **not** point the aggregator at `neelastack-prod-db-backups`: Cloudflare's standard Object Read Only token is scoped to buckets, so using the backup bucket could expose database backup objects. Cloudflare documents bucket-scoped Object Read Only access in its [R2 token documentation](https://developers.cloudflare.com/r2/api/tokens/); temporary credentials can optionally be narrowed further by prefix, but the design here isolates evidence into its own bucket for simpler unattended operation.

Configure these settings in both GitHub Environments, `single-node-production-readiness` and `readiness-evidence-audit`:

- Environment Variables: `R2_EVIDENCE_BUCKET=neelastack-readiness-evidence`, `R2_ENDPOINT_URL`.
- Environment Secrets: `R2_EVIDENCE_READONLY_ACCESS_KEY_ID`, `R2_EVIDENCE_READONLY_SECRET_ACCESS_KEY`.

Create the read-only S3-compatible token with **Object Read Only** permission for the dedicated evidence bucket only. It must not have write/delete permission or access to the backup bucket. Separately create an **Object Read & Write** token scoped to that same evidence bucket, and configure it as AWS CLI profile `neelastack-r2-evidence` on the Windows authoring machine and the staging VM. The local publisher and payment-chaos runner use only that writer profile; the GitHub workflows use only the read-only token. Keep the existing `neelastack-r2` backup profile and backup bucket settings for backup/PITR operations.

Configure and verify an R2 Bucket Lock/retention rule for the evidence objects under `production/readiness-evidence/` before treating the store as immutable. The publisher also uses unique filenames and refuses to overwrite different bytes, but client-side checks are not a replacement for server-side retention.

Keep the `single-node-production-readiness` Environment's reviewer approval requirement enabled. That protected approval is still part of the attestation; the aggregator cannot replace it. The scheduled read-only audit uses the separate `readiness-evidence-audit` Environment without required reviewers so it can run unattended; it only verifies freshness/checksums and reports blockers—it never creates the certification manifest or deploys.

The following actions still require honest human input and are not auto-approved: independent penetration-test report/review, finance control-total reconciliation, privacy/legal review, evidence scope acceptance, and measured recovery RPO/RTO. RPO/RTO are taken from the reviewed `SINGLE_NODE_RECOVERY` result, never free-standing variables. The policy defaults are 300 seconds RPO and 1,800 seconds RTO; any change to those bounds must be reviewed in source.

## Release sequence

1. Run **Enterprise Release Qualification** for the exact release SHA and record the successful run ID. This validates the source/backend/frontend/database/browser/load/DAST gates; it is not operational certification.
2. Complete the operational exercises and configure their real evidence variables in `single-node-production-readiness`.
3. Run **Single-Node Production Readiness** with that exact SHA and qualification run ID. It verifies the matching qualification artifact and creates `single-node-production-readiness-<SHA>` only if all evidence passes. The manifest explicitly sets `enterpriseHACertificationGranted=false`.
4. Review the readiness manifest and supporting records. The successful run publishes `single-node-production-readiness-<SHA>` and its JSON manifest.
5. Run **Production** with the same release SHA and type `SINGLE-NODE-BREAK-GLASS` into the confirmation field. The production workflow locates the successful protected readiness artifact for that exact SHA, verifies its status/topology/evidence identifiers and rejects attestations older than 90 days. There is no manually maintained production topology/node-count/SHA variable. GitHub's protected `production` environment approval remains mandatory, and image signature/provenance plus smoke checks remain required.

The production workflow does not accept the HA certified SHA as a substitute for single-node readiness. Conversely, the single-node workflow cannot produce the enterprise HA certification artifact. These are distinct attestations. Set realistic RPO/RTO targets after measuring them; a single VM has a single-node failure domain and may remain unavailable until restored.
