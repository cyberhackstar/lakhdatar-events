# Enterprise certification environment

Create a protected GitHub Environment named `enterprise-certification` and require reviewer approval. Populate the following environment variables only after the corresponding evidence has actually been executed and reviewed:

- `PRODUCTION_TOPOLOGY=enterprise-ha`
- `HA_NODE_COUNT` (minimum 2)
- `RPO_SECONDS` and `RTO_SECONDS`
- `HA_FAILOVER_STATUS=PASS` and `HA_FAILOVER_EVIDENCE_ID`
- `PITR_STATUS=PASS` and `PITR_EVIDENCE_ID`
- `PEN_TEST_STATUS=PASS` and `PEN_TEST_EVIDENCE_ID`
- `PAYMENT_CHAOS_STATUS=PASS` and `PAYMENT_CHAOS_EVIDENCE_ID`
- `ALERTING_STATUS=PASS` and `ALERTING_EVIDENCE_ID`
- `FINANCE_RECONCILIATION_STATUS=PASS` and `FINANCE_RECONCILIATION_EVIDENCE_ID`
- `PRIVACY_REVIEW_STATUS=PASS` and `PRIVACY_REVIEW_EVIDENCE_ID`
- `MFA_STATUS=PASS` and `MFA_EVIDENCE_ID` for the privileged-MFA enablement and recovery drill

The certification workflow refuses to produce a PASS manifest when any item is missing. These values represent real external operational evidence; they must not be populated merely to make CI green.
