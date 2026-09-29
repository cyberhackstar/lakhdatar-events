#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/infra/backups}"
SECONDARY_BACKUP_DIR="${SECONDARY_BACKUP_DIR:-}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-lakhdatar-postgres}"
POSTGRES_USER="${POSTGRES_USER:-lakhdatar}"
POSTGRES_DB="${POSTGRES_DB:-lakhdatar}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
ALLOW_MISSING="${1:-}"

umask 077
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

if ! docker inspect -f '{{.State.Running}}' "$POSTGRES_CONTAINER" 2>/dev/null | grep -qx true; then
  if [[ "$ALLOW_MISSING" == "--allow-missing" ]]; then
    echo "No running PostgreSQL container ($POSTGRES_CONTAINER); first deployment may proceed without a pre-deployment backup."
    exit 0
  fi
  echo "PostgreSQL container ($POSTGRES_CONTAINER) is not running; backup FAILED." >&2
  exit 1
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="$BACKUP_DIR/lakhdatar-$STAMP.dump"
TMP="$FILE.tmp"

cleanup(){ rm -f "$TMP"; }
trap cleanup EXIT

echo "Creating PostgreSQL backup: $FILE"
docker exec "$POSTGRES_CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$TMP"
test -s "$TMP"
mv "$TMP" "$FILE"
chmod 600 "$FILE"
sha256sum "$FILE" > "$FILE.sha256"
chmod 600 "$FILE.sha256"

if [[ -n "$SECONDARY_BACKUP_DIR" ]]; then
  mkdir -p "$SECONDARY_BACKUP_DIR"
  chmod 700 "$SECONDARY_BACKUP_DIR"
  cp "$FILE" "$FILE.sha256" "$SECONDARY_BACKUP_DIR/"
  chmod 600 "$SECONDARY_BACKUP_DIR/$(basename "$FILE")" "$SECONDARY_BACKUP_DIR/$(basename "$FILE.sha256")"
  (cd "$SECONDARY_BACKUP_DIR" && sha256sum -c "$(basename "$FILE.sha256")")
fi

find "$BACKUP_DIR" -type f -name 'lakhdatar-*.dump' -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -type f -name 'lakhdatar-*.dump.sha256' -mtime "+$RETENTION_DAYS" -delete

echo "Backup complete: $FILE"
