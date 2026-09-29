#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP="${1:-}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-lakhdatar-postgres}"
POSTGRES_USER="${POSTGRES_USER:-lakhdatar}"
POSTGRES_DB="${POSTGRES_DB:-lakhdatar}"

if [[ -z "$BACKUP" || ! -f "$BACKUP" ]]; then echo "Usage: $0 /path/to/lakhdatar-<timestamp>.dump" >&2; exit 2; fi
if [[ -f "$BACKUP.sha256" ]]; then (cd "$(dirname "$BACKUP")" && sha256sum -c "$(basename "$BACKUP.sha256")"); fi

echo "WARNING: this restores the selected backup into database $POSTGRES_DB."
read -r -p "Type RESTORE to continue: " CONFIRM
[[ "$CONFIRM" == "RESTORE" ]] || { echo "Restore cancelled."; exit 1; }

docker exec -i "$POSTGRES_CONTAINER" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges < "$BACKUP"
echo "Restore completed. Validate application health and critical business records before resuming traffic."
