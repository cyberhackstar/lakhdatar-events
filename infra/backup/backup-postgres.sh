#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/infra/backups}"
SECONDARY_BACKUP_DIR="${SECONDARY_BACKUP_DIR:-}"
BACKUP_REMOTE_URI="${BACKUP_REMOTE_URI:-}"
BACKUP_REMOTE_REQUIRED="${BACKUP_REMOTE_REQUIRED:-false}"
BACKUP_S3_ENDPOINT_URL="${BACKUP_S3_ENDPOINT_URL:-}"
BACKUP_S3_SSE="${BACKUP_S3_SSE:-AES256}"
BACKUP_S3_KMS_KEY_ID="${BACKUP_S3_KMS_KEY_ID:-}"
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

# A first deployment has no existing data to protect. Every later deployment must satisfy the
# independent-backup requirement before returning success.
if [[ "${BACKUP_REMOTE_REQUIRED,,}" == "true" && -z "$BACKUP_REMOTE_URI" ]]; then
  echo "Independent remote backup is required but BACKUP_REMOTE_URI is not configured." >&2
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

if [[ -n "$BACKUP_REMOTE_URI" ]]; then
  command -v aws >/dev/null 2>&1 || { echo "BACKUP_REMOTE_URI is configured but AWS CLI is not installed." >&2; exit 1; }
  if [[ ! "$BACKUP_REMOTE_URI" =~ ^s3://[^/]+(/.*)?$ ]]; then
    echo "BACKUP_REMOTE_URI must use s3://bucket[/prefix]." >&2
    exit 1
  fi
  REMOTE_NO_SCHEME="${BACKUP_REMOTE_URI#s3://}"
  REMOTE_BUCKET="${REMOTE_NO_SCHEME%%/*}"
  if [[ "$REMOTE_NO_SCHEME" == "$REMOTE_BUCKET" ]]; then
    REMOTE_KEY_PREFIX=""
  else
    REMOTE_KEY_PREFIX="${REMOTE_NO_SCHEME#*/}"
    REMOTE_KEY_PREFIX="${REMOTE_KEY_PREFIX#/}"
    REMOTE_KEY_PREFIX="${REMOTE_KEY_PREFIX%/}"
  fi
  if [[ -n "$REMOTE_KEY_PREFIX" ]]; then
    REMOTE_KEY_PREFIX="${REMOTE_KEY_PREFIX}/"
  fi
  REMOTE_KEY="${REMOTE_KEY_PREFIX}$(basename "$FILE")"
  REMOTE_SHA_KEY="${REMOTE_KEY_PREFIX}$(basename "$FILE.sha256")"
  AWS_ARGS=()
  if [[ -n "$BACKUP_S3_ENDPOINT_URL" ]]; then AWS_ARGS+=(--endpoint-url "$BACKUP_S3_ENDPOINT_URL"); fi
  SSE_ARGS=()
  case "${BACKUP_S3_SSE,,}" in
    aes256) SSE_ARGS+=(--sse AES256) ;;
    aws:kms)
      [[ -n "$BACKUP_S3_KMS_KEY_ID" ]] || { echo "BACKUP_S3_KMS_KEY_ID is required when BACKUP_S3_SSE=aws:kms." >&2; exit 1; }
      SSE_ARGS+=(--sse aws:kms --sse-kms-key-id "$BACKUP_S3_KMS_KEY_ID")
      ;;
    *) echo "BACKUP_S3_SSE must be AES256 or aws:kms." >&2; exit 1 ;;
  esac
  echo "Uploading encrypted backup artifact to independent object storage: s3://${REMOTE_BUCKET}/${REMOTE_KEY}"
  aws "${AWS_ARGS[@]}" s3 cp "$FILE" "s3://${REMOTE_BUCKET}/${REMOTE_KEY}" "${SSE_ARGS[@]}" --only-show-errors
  aws "${AWS_ARGS[@]}" s3 cp "$FILE.sha256" "s3://${REMOTE_BUCKET}/${REMOTE_SHA_KEY}" "${SSE_ARGS[@]}" --only-show-errors
  aws "${AWS_ARGS[@]}" s3api head-object --bucket "$REMOTE_BUCKET" --key "$REMOTE_KEY" >/dev/null
  aws "${AWS_ARGS[@]}" s3api head-object --bucket "$REMOTE_BUCKET" --key "$REMOTE_SHA_KEY" >/dev/null
fi

echo "Backup complete: $FILE"
