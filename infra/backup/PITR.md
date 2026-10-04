# PostgreSQL PITR / disaster recovery standard

Logical `pg_dump` backups remain useful for migrations and point-in-time snapshots, but they are not a substitute for PostgreSQL WAL archiving when the business requires a small recovery-point objective.

## Target for enterprise production

Choose and document an RPO/RTO with the business. A practical starting target is:

- RPO: less than 5 minutes
- RTO: less than 30 minutes

The exact values must be validated with actual restore drills.

## Required architecture

```text
PostgreSQL primary
      |
      +--> continuous WAL archive --> encrypted off-host object storage
      |
      +--> standby/managed replica

Object storage
      |
      +--> immutable retention
      +--> lifecycle policy
      +--> restore drill environment
```

Do not point `archive_command` at the application VM only. A VM loss must not remove both database and WAL history.

## Restore drill

At least monthly, restore a clean PostgreSQL instance to a separate environment, replay WAL to a known timestamp, run the application health checks, and verify critical records: users, organizers, events, reservations, orders, payments, refunds and tickets.

Measure the actual restore time and record the last replayed LSN/timestamp. A backup policy is not considered proven until the restore drill has succeeded.

## Current application support

The application already uses Flyway and `ddl-auto=validate`, so a restored database must pass Flyway validation before traffic is resumed. Never restore a production snapshot directly over a live database without an approved outage/recovery procedure.
