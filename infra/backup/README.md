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
BACKUP_AWS_PROFILE=neelastack-r2
BACKUP_S3_SSE=none
BACKUP_MAX_REMOTE_BYTES=7000000000
# R2 encrypts objects at rest automatically; do not pass the ordinary S3 --sse header to R2.
# For AWS S3 you may use BACKUP_S3_SSE=AES256 or BACKUP_S3_SSE=aws:kms with BACKUP_S3_KMS_KEY_ID.
# Keep R2 credentials in the Ubuntu user's ~/.aws/credentials file under the named profile.
```

Use bucket lifecycle rules to keep daily/weekly/monthly recovery points. The deploy script verifies the local backup before release; the backup script verifies the remote object was created before reporting success.

A quarterly restore drill should restore the newest backup into an isolated PostgreSQL instance and run application migration/startup plus business invariants (orders, payments, refunds, tickets, check-ins). Record the measured RPO/RTO.

## Disaster-recovery restore drill

Run `infra/backup/restore-drill.sh` against a disposable PostgreSQL target after verifying the backup checksum. The script intentionally requires `DRILL_CONFIRM=RESTORE-DRILL` and never discovers or selects a production database for you. Record the observed RPO/RTO and verify orders, payments, refunds, tickets, Flyway history, and application startup before declaring the drill successful.

### Backup storage invariants

The backup job rejects malformed `s3://bucket[/prefix]` destinations, selects the configured AWS CLI profile, checks the bucket's listed object bytes against `BACKUP_MAX_REMOTE_BYTES`, verifies uploaded object sizes and checksum sidecar, and streams the remote dump back through SHA-256 before reporting success. The guard is an application-level safety threshold, not a Cloudflare billing cap; monitor account-level R2 usage too. Cloudflare R2 encrypts objects at rest automatically, so use `BACKUP_S3_SSE=none` for R2 (the ordinary S3 `x-amz-server-side-encryption` header is not supported by R2). If `BACKUP_S3_SSE` is omitted, the scripts infer `none` from a Cloudflare R2 endpoint and use `AES256` for other S3 endpoints. Do not put credentials in the repository or `.env`; store them in the protected Ubuntu user's AWS CLI credentials file.
