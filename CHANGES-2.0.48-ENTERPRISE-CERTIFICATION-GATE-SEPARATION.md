# v2.0.48 — Separate release qualification from enterprise production certification

## Root cause

The v2.0.47 enterprise release qualification run reached its final `enterprise-evidence` job after the prerequisite source, backend, frontend, database, browser E2E, load and DAST jobs had completed successfully. That last job failed because the protected `enterprise-certification` environment supplied none of its required operational evidence values (`HA_FAILOVER_EVIDENCE_ID` was the first reported failure; the log showed the remaining evidence IDs, statuses, topology and measured RPO/RTO settings empty as well).

This is not an application/load-test failure. It is an operational certification configuration/evidence prerequisite. The former workflow coupled release testing to independently executed production-operations drills, so a qualified release could not finish with a clear, accurate qualification result before those drills had occurred.

## Changes

- Decoupled release qualification (source/security, backend, frontend, database migration, browser E2E, staging load and DAST) from enterprise production certification.
- The release qualification workflow now uploads `release-qualification-<SHA>` with the exact SHA, workflow run ID, required passed gates, and an explicit `productionCertificationGranted=false` statement.
- Added a separate protected `Enterprise Production Certification` manual workflow. It requires the exact SHA and successful qualification run ID, downloads the matching artifact, validates the embedded SHA/run ID/gate list, then checks the protected operational evidence.
- Production certification still fails closed if real HA, PITR, penetration/security, payment-chaos, alerting, finance reconciliation, privacy review, or privileged MFA evidence is missing/invalid or measured RPO/RTO exceeds policy.
- The evidence validator now reports all missing/invalid fields in one run, avoids creating a stale PASS manifest after failed validation, and fixes HA node-count validation to correctly accept counts of 10 or more.
- Added regression tests proving the qualification/certification separation, aggregated diagnostics, evidence manifest integrity, preserved RPO/RTO controls, and the unchanged protected production SHA gate.

## Security boundary preserved

A passing release qualification does not set `ENTERPRISE_CERTIFIED_SHA`, generate an enterprise certification manifest, or authorize deployment. The production and production-HA workflows still require the separately protected certified SHA and their existing topology/provenance guards. No operational evidence or PASS status is fabricated.
