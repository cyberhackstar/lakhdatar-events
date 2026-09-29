# Backup and disaster recovery

## Backup policy

`infra/backup/backup-postgres.sh` writes a PostgreSQL custom-format dump plus SHA-256 checksum. Scheduled runs fail when PostgreSQL is unavailable; the deployment script alone uses `--allow-missing` for a true first install with no existing database.

Recommended operational policy:

- automated daily backup during low traffic (see `infra/backup/install-cron.sh`)
- keep at least 14 days online
- copy to storage independent from the production VM
- periodically copy a longer-retention snapshot
- periodically perform a real restore test

## Secondary backup

Set:

```bash
export SECONDARY_BACKUP_DIR=/mnt/remote-backups/lakhdatar
```

The directory must not be merely another folder on the same production disk if disaster recovery is the goal.

## Restore

```bash
./infra/backup/restore-postgres.sh /mnt/remote-backups/lakhdatar/lakhdatar-<timestamp>.dump
```

The script verifies the checksum when present and requires typing `RESTORE`.

After restore:

1. validate schema and application health
2. inspect representative orders/payments/tickets/check-ins
3. verify payment/ticket consistency
4. only then reopen traffic

## Backup failure

Backup failure is a production incident. Configure monitoring around the backup script exit status and disk space.
