# Enterprise PostgreSQL backup / DR

The production deployment is designed for **independent** backup storage, not a second directory on the same VM. Configure `BACKUP_REMOTE_URI` to an S3-compatible prefix (AWS S3, Cloudflare R2, or equivalent) and set `BACKUP_REMOTE_REQUIRED=true` for existing production data.

Required host tooling:

```text
aws --version
```

Example (Cloudflare R2):

```text
BACKUP_REMOTE_REQUIRED=true
BACKUP_REMOTE_URI=s3://neelastack-events-backups/postgres
BACKUP_S3_ENDPOINT_URL=https://<account-id>.r2.cloudflarestorage.com
BACKUP_S3_SSE=AES256
# For AWS KMS instead: BACKUP_S3_SSE=aws:kms and set BACKUP_S3_KMS_KEY_ID=<key-arn-or-id>
AWS_ACCESS_KEY_ID=<R2 access key>
AWS_SECRET_ACCESS_KEY=<R2 secret>
```

Use bucket lifecycle rules to keep daily/weekly/monthly recovery points. The deploy script verifies the local backup before release; the backup script verifies the remote object was created before reporting success.

A quarterly restore drill should restore the newest backup into an isolated PostgreSQL instance and run application migration/startup plus business invariants (orders, payments, refunds, tickets, check-ins). Record the measured RPO/RTO.

## Disaster-recovery restore drill

Run `infra/backup/restore-drill.sh` against a disposable PostgreSQL target after verifying the backup checksum. The script intentionally requires `DRILL_CONFIRM=RESTORE-DRILL` and never discovers or selects a production database for you. Record the observed RPO/RTO and verify orders, payments, refunds, tickets, Flyway history, and application startup before declaring the drill successful.

### Backup storage invariants

The backup job rejects malformed `s3://bucket[/prefix]` destinations and verifies both the dump and checksum object with `head-object`. Backups are uploaded with explicit server-side encryption (`AES256` by default or AWS KMS when configured); do not rely on bucket defaults as the only encryption control.
