#!/usr/bin/env bash
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-/var/backups/lakhdatar-events}"
latest="$(ls -1t "$BACKUP_DIR"/*.dump 2>/dev/null | head -n1 || true)"
[ -n "$latest" ] || { echo "No PostgreSQL dump found in $BACKUP_DIR" >&2; exit 1; }
work="$(mktemp -d)"
cleanup(){ rm -rf "$work"; }
trap cleanup EXIT
container="lakhdatar-restore-test-$$"
docker run -d --rm --name "$container" -e POSTGRES_PASSWORD=restore-test -e POSTGRES_DB=restore postgres:16-alpine >/dev/null
cleanup_container(){ docker rm -f "$container" >/dev/null 2>&1 || true; }
trap 'cleanup_container; cleanup' EXIT
for i in $(seq 1 30); do docker exec "$container" pg_isready -U postgres -d restore >/dev/null 2>&1 && break; sleep 2; done
docker exec "$container" pg_isready -U postgres -d restore >/dev/null
docker cp "$latest" "$container:/tmp/restore.dump"
docker exec "$container" pg_restore -U postgres -d restore --exit-on-error --no-owner /tmp/restore.dump
echo "Backup restore verification passed: $latest"
