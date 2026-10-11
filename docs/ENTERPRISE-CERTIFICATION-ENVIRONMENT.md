# Enterprise certification environment

## Protected GitHub environment

Create a protected GitHub Environment named `enterprise-certification` and require reviewer approval. Add the following as **GitHub Environment Variables** (Settings → Environments → `enterprise-certification` → Environment variables), because the workflow reads them from the `${{ vars.NAME }}` context. Creating same-named secrets will not populate those expressions.

Populate the settings only after their corresponding real exercise/review has been completed, results have been reviewed, and the supporting report or immutable evidence record is retained. Never use placeholder IDs or set a status to `PASS` to make CI green.

- `PRODUCTION_TOPOLOGY=enterprise-ha`
- `HA_NODE_COUNT` (integer, minimum 2; counts of 10 or more are accepted)
- `RPO_SECONDS` and `RTO_SECONDS` (measured numeric values from the real restore/failover drills)
- `MAX_RPO_SECONDS` (optional; defaults to `300`)
- `MAX_RTO_SECONDS` (optional; defaults to `1800`)
- `HA_FAILOVER_STATUS=PASS` and `HA_FAILOVER_EVIDENCE_ID`
- `PITR_STATUS=PASS` and `PITR_EVIDENCE_ID`
- `PEN_TEST_STATUS=PASS` and `PEN_TEST_EVIDENCE_ID`
- `PAYMENT_CHAOS_STATUS=PASS` and `PAYMENT_CHAOS_EVIDENCE_ID`
- `ALERTING_STATUS=PASS` and `ALERTING_EVIDENCE_ID`
- `FINANCE_RECONCILIATION_STATUS=PASS` and `FINANCE_RECONCILIATION_EVIDENCE_ID`
- `PRIVACY_REVIEW_STATUS=PASS` and `PRIVACY_REVIEW_EVIDENCE_ID`
- `MFA_STATUS=PASS` and `MFA_EVIDENCE_ID` for the privileged-MFA enablement and recovery drill

Evidence IDs must be stable references (6–256 characters; letters, digits, `.`, `_`, `:`, `/`, and `-` are accepted). They may refer to retained report IDs, ticket IDs, or immutable evidence records; they are not themselves proof unless the related evidence actually exists and has been reviewed.

## Run order

1. Run **Enterprise Release Qualification** for the exact release commit and staging URL. Wait for all source, backend, frontend, database, browser E2E, load, and DAST jobs to pass. Record the run ID and exact `release_sha`.
2. Complete and review all operational exercises above. For topology `enterprise-ha`, the production system must actually meet the declared multiple-node architecture.
3. Run **Enterprise Production Certification** with that same `release_sha` and the successful qualification `qualification_run_id`. The workflow verifies the matching release-qualification artifact before checking the operational evidence.
4. Only after reviewing the resulting `enterprise-certification-<SHA>` artifact and supporting reports should an authorized reviewer set the protected `ENTERPRISE_CERTIFIED_SHA` variable for production promotion.

The certification workflow refuses to produce a PASS manifest when any item is missing, invalid, or over the configured RPO/RTO limits. Its validator prints all missing/invalid fields in one run. A green release-qualification workflow alone does not certify the production deployment.
