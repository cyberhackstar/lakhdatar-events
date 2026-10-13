#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/infra/backups}"
SECONDARY_BACKUP_DIR="${SECONDARY_BACKUP_DIR:-}"
BACKUP_REMOTE_URI="${BACKUP_REMOTE_URI:-}"
BACKUP_REMOTE_REQUIRED="${BACKUP_REMOTE_REQUIRED:-false}"
BACKUP_S3_ENDPOINT_URL="${BACKUP_S3_ENDPOINT_URL:-}"
BACKUP_S3_SSE="${BACKUP_S3_SSE:-}"
if [[ -z "$BACKUP_S3_SSE" ]]; then
  case "$BACKUP_S3_ENDPOINT_URL" in
    https://*.r2.cloudflarestorage.com) BACKUP_S3_SSE="none" ;;
    *) BACKUP_S3_SSE="AES256" ;;
  esac
fi
BACKUP_S3_KMS_KEY_ID="${BACKUP_S3_KMS_KEY_ID:-}"
BACKUP_AWS_PROFILE="${BACKUP_AWS_PROFILE:-default}"
BACKUP_MAX_REMOTE_BYTES="${BACKUP_MAX_REMOTE_BYTES:-7000000000}"
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
if [[ ! "$BACKUP_AWS_PROFILE" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "BACKUP_AWS_PROFILE must contain only letters, digits, underscores, and hyphens." >&2
  exit 1
fi
if [[ ! "$BACKUP_MAX_REMOTE_BYTES" =~ ^[1-9][0-9]{0,11}$ ]]; then
  echo "BACKUP_MAX_REMOTE_BYTES must be a positive integer no longer than 12 digits." >&2
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
  AWS_ARGS=(--profile "$BACKUP_AWS_PROFILE")
  if [[ -n "$BACKUP_S3_ENDPOINT_URL" ]]; then AWS_ARGS+=(--endpoint-url "$BACKUP_S3_ENDPOINT_URL"); fi
  SSE_ARGS=()
  case "${BACKUP_S3_SSE,,}" in
    none|disabled|off)
      # Cloudflare R2 encrypts objects at rest automatically and does not implement
      # the ordinary x-amz-server-side-encryption request header. Use BACKUP_S3_SSE=none.
      ;;
    aes256) SSE_ARGS+=(--sse AES256) ;;
    aws:kms)
      [[ -n "$BACKUP_S3_KMS_KEY_ID" ]] || { echo "BACKUP_S3_KMS_KEY_ID is required when BACKUP_S3_SSE=aws:kms." >&2; exit 1; }
      SSE_ARGS+=(--sse aws:kms --sse-kms-key-id "$BACKUP_S3_KMS_KEY_ID")
      ;;
    *) echo "BACKUP_S3_SSE must be none, AES256, or aws:kms." >&2; exit 1 ;;
  esac

  echo "Checking remote object storage usage against ${BACKUP_MAX_REMOTE_BYTES} bytes."
  REMOTE_USED_RAW="$(aws "${AWS_ARGS[@]}" s3api list-objects-v2 --bucket "$REMOTE_BUCKET" --query 'sum(Contents[].Size)' --output text)"
  REMOTE_USED_BYTES="$(printf '%s\n' "$REMOTE_USED_RAW" | awk '/^[0-9]+([.][0-9]+)?$/ { total += $1 } END { printf "%.0f\n", total }')"
  FILE_BYTES="$(stat -c '%s' "$FILE")"
  SHA_BYTES="$(stat -c '%s' "$FILE.sha256")"
  if (( REMOTE_USED_BYTES + FILE_BYTES + SHA_BYTES > BACKUP_MAX_REMOTE_BYTES )); then
    echo "Remote backup storage guard refused upload: current=${REMOTE_USED_BYTES} bytes, new=$((FILE_BYTES + SHA_BYTES)) bytes, threshold=${BACKUP_MAX_REMOTE_BYTES} bytes." >&2
    echo "The local backup was retained at $FILE" >&2
    exit 1
  fi

  echo "Uploading backup artifact to independent object storage: s3://${REMOTE_BUCKET}/${REMOTE_KEY}"
  aws "${AWS_ARGS[@]}" s3 cp "$FILE" "s3://${REMOTE_BUCKET}/${REMOTE_KEY}" "${SSE_ARGS[@]}" --only-show-errors
  aws "${AWS_ARGS[@]}" s3 cp "$FILE.sha256" "s3://${REMOTE_BUCKET}/${REMOTE_SHA_KEY}" "${SSE_ARGS[@]}" --only-show-errors
  REMOTE_SIZE="$(aws "${AWS_ARGS[@]}" s3api head-object --bucket "$REMOTE_BUCKET" --key "$REMOTE_KEY" --query ContentLength --output text)"
  REMOTE_SHA_SIZE="$(aws "${AWS_ARGS[@]}" s3api head-object --bucket "$REMOTE_BUCKET" --key "$REMOTE_SHA_KEY" --query ContentLength --output text)"
  [[ "$REMOTE_SIZE" =~ ^[0-9]+$ && "$REMOTE_SIZE" == "$(stat -c '%s' "$FILE")" ]] || { echo "Remote backup size verification failed." >&2; exit 1; }
  [[ "$REMOTE_SHA_SIZE" =~ ^[0-9]+$ && "$REMOTE_SHA_SIZE" == "$(stat -c '%s' "$FILE.sha256")" ]] || { echo "Remote checksum sidecar size verification failed." >&2; exit 1; }

  LOCAL_SHA="$(awk 'NR==1 {print $1}' "$FILE.sha256")"
  REMOTE_SHA_SIDECAR="$(aws "${AWS_ARGS[@]}" s3 cp "s3://${REMOTE_BUCKET}/${REMOTE_SHA_KEY}" - --only-show-errors | awk 'NR==1 {print $1}')"
  [[ "$REMOTE_SHA_SIDECAR" == "$LOCAL_SHA" ]] || { echo "Remote checksum sidecar content verification failed." >&2; exit 1; }
  REMOTE_PAYLOAD_SHA="$(aws "${AWS_ARGS[@]}" s3 cp "s3://${REMOTE_BUCKET}/${REMOTE_KEY}" - --only-show-errors | sha256sum | awk '{print $1}')"
  [[ "$REMOTE_PAYLOAD_SHA" == "$LOCAL_SHA" ]] || { echo "Remote backup download SHA-256 verification failed." >&2; exit 1; }
  echo "Remote backup verified by size and downloaded SHA-256."
fi

# Prune local copies only after the remote upload (when configured) succeeds.
find "$BACKUP_DIR" -type f -name 'lakhdatar-*.dump' -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -type f -name 'lakhdatar-*.dump.sha256' -mtime "+$RETENTION_DAYS" -delete

echo "Backup complete: $FILE"
