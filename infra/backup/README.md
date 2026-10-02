# Production backup and recovery

Backups are database dumps only; copy them off the VM and encrypt them using your approved KMS/object-storage workflow.

Run `verify-latest-backup.sh` after each successful dump. The verifier starts a temporary PostgreSQL 16 container and performs a full `pg_restore --exit-on-error`, which catches unusable/corrupt dumps.

For enterprise DR, establish a tested RPO/RTO, off-host retention, and a clean-VM restore drill. Do not treat a local dump alone as a disaster-recovery guarantee.
