#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCHEDULE="${BACKUP_CRON_SCHEDULE:-17 3 * * *}"
RETENTION="${BACKUP_RETENTION_DAYS:-14}"
SECONDARY="${SECONDARY_BACKUP_DIR:-}"
mkdir -p "$ROOT/infra/backups"
if [[ -z "$SECONDARY" ]]; then
  echo "WARNING: SECONDARY_BACKUP_DIR is empty. The scheduled backup will have no off-host copy." >&2
fi
LINE="$SCHEDULE cd $ROOT && BACKUP_RETENTION_DAYS=$RETENTION SECONDARY_BACKUP_DIR=$SECONDARY $ROOT/infra/backup/backup-postgres.sh >> $ROOT/infra/backups/backup.log 2>&1"
( crontab -l 2>/dev/null | grep -vF "$ROOT/infra/backup/backup-postgres.sh" || true; echo "$LINE" ) | crontab -
echo "Installed daily Lakhdatar PostgreSQL backup: $SCHEDULE"
