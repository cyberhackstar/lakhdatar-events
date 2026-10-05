# Disaster recovery certification

Run this against a disposable restore environment, never the primary production database.

1. Confirm the latest encrypted backup and WAL/PITR chain.
2. Restore the database into an isolated target.
3. Replay WAL to the chosen recovery point.
4. Start the backend against the restored database.
5. Run schema/version checks and the critical E2E read suite.
6. Verify order/payment/refund/ticket/check-in/audit records.
7. Record measured RPO and RTO.
8. Capture checksum/object identifiers and operator sign-off.

Do not call the environment “PITR-ready” solely because backup scripts exist; the restore must actually complete and be reviewed.
