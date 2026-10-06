#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCHEDULE="${BACKUP_CRON_SCHEDULE:-17 3 * * *}"
RETENTION="${BACKUP_RETENTION_DAYS:-14}"
SECONDARY="${SECONDARY_BACKUP_DIR:-}"
BACKUP_REMOTE_REQUIRED="${BACKUP_REMOTE_REQUIRED:-true}"
BACKUP_REMOTE_URI="${BACKUP_REMOTE_URI:-}"
BACKUP_S3_ENDPOINT_URL="${BACKUP_S3_ENDPOINT_URL:-}"
BACKUP_S3_SSE="${BACKUP_S3_SSE:-AES256}"
BACKUP_S3_KMS_KEY_ID="${BACKUP_S3_KMS_KEY_ID:-}"
mkdir -p "$ROOT/infra/backups"
if [[ "${BACKUP_REMOTE_REQUIRED,,}" == "true" && -z "${BACKUP_REMOTE_URI:-}" ]]; then
  echo "ERROR: BACKUP_REMOTE_REQUIRED=true but BACKUP_REMOTE_URI is empty. Refusing to install a non-independent production backup schedule." >&2
  exit 2
fi
if [[ -z "$SECONDARY" ]]; then
  echo "Info: SECONDARY_BACKUP_DIR is empty; independent protection is provided by BACKUP_REMOTE_URI." >&2
fi
LINE="$SCHEDULE cd $ROOT && BACKUP_RETENTION_DAYS=$RETENTION BACKUP_REMOTE_REQUIRED=$BACKUP_REMOTE_REQUIRED BACKUP_REMOTE_URI='${BACKUP_REMOTE_URI}' BACKUP_S3_ENDPOINT_URL='${BACKUP_S3_ENDPOINT_URL:-}' BACKUP_S3_SSE='${BACKUP_S3_SSE:-AES256}' BACKUP_S3_KMS_KEY_ID='${BACKUP_S3_KMS_KEY_ID:-}' SECONDARY_BACKUP_DIR=$SECONDARY $ROOT/infra/backup/backup-postgres.sh >> $ROOT/infra/backups/backup.log 2>&1"
( crontab -l 2>/dev/null | grep -vF "$ROOT/infra/backup/backup-postgres.sh" || true; echo "$LINE" ) | crontab -
echo "Installed daily Lakhdatar PostgreSQL backup: $SCHEDULE"
