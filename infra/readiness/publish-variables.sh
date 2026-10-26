#!/usr/bin/env bash
# Validate, upload, and byte-verify readiness evidence in Cloudflare R2 before publishing GitHub variables.
# Dry run: bash infra/readiness/publish-variables.sh [readiness-evidence/variables.env]
# Upload/verify only: UPLOAD_EVIDENCE=yes R2_BUCKET=... R2_ENDPOINT_URL=... AWS_PROFILE=neelastack-r2 bash ...
# Publish: UPLOAD_EVIDENCE=yes APPLY=yes R2_BUCKET=... R2_ENDPOINT_URL=... AWS_PROFILE=neelastack-r2 REPO=owner/repo bash ...
set -Eeuo pipefail
FILE="${1:-${EVIDENCE_DIR:-$PWD/readiness-evidence}/variables.env}"
ENVNAME="${ENV_NAME:-single-node-production-readiness}"
REPO="${REPO:-cyberhackstar/lakhdatar-events}"
EVIDENCE_DIR="${EVIDENCE_DIR:-$(dirname "$FILE")}"
[[ -f "$FILE" ]] || { echo "No $FILE. Run the drills first (see infra/readiness/README.md)." >&2; exit 2; }
DUPLICATES="$(awk -F= 'NF && $1 !~ /^[[:space:]]*#/ { if (seen[$1]++) print $1 }' "$FILE" | sort -u)"
[[ -z "$DUPLICATES" ]] || { echo "Duplicate keys in $FILE (would publish stale values): $DUPLICATES" >&2; exit 2; }
get() { grep -E "^$1=" "$FILE" | tail -1 | cut -d= -f2- || true; }
fail() { echo "ERROR: $*" >&2; exit 1; }

missing=0
missing_reasons=()
for p in SINGLE_NODE_RECOVERY OFFSITE_BACKUP PITR PEN_TEST PAYMENT_CHAOS ALERTING FINANCE_RECONCILIATION PRIVACY_REVIEW MFA; do
  [[ "$(get ${p}_STATUS)" == PASS && "$(get ${p}_EVIDENCE_ID)" =~ ^[A-Za-z0-9._:/-]{6,256}$ ]] || {
    echo "READINESS BLOCKER: $p is missing, invalid, or not PASS" >&2
    missing=1; missing_reasons+=("$p")
  }
done
for v in RPO_SECONDS RTO_SECONDS; do
  [[ "$(get "$v")" =~ ^[0-9]{1,9}$ ]] || { echo "READINESS BLOCKER: $v is missing or invalid" >&2; missing=1; missing_reasons+=("$v"); }
done
RPO="$(get RPO_SECONDS)"; RTO="$(get RTO_SECONDS)"
MAX_RPO_SECONDS="${MAX_RPO_SECONDS:-300}"; MAX_RTO_SECONDS="${MAX_RTO_SECONDS:-1800}"
[[ "$MAX_RPO_SECONDS" =~ ^[0-9]{1,9}$ && "$MAX_RTO_SECONDS" =~ ^[0-9]{1,9}$ ]] || fail "MAX_RPO_SECONDS/MAX_RTO_SECONDS must be integers"
if [[ "$RPO" =~ ^[0-9]{1,9}$ && "$RTO" =~ ^[0-9]{1,9}$ ]]; then
  if (( 10#$RPO > 10#$MAX_RPO_SECONDS || 10#$RTO > 10#$MAX_RTO_SECONDS )); then
    echo "READINESS BLOCKER: RPO/RTO exceed policy maximums" >&2
    missing=1; missing_reasons+=("RPO/RTO policy limits")
  fi
fi

# Evidence upload is useful even while another control is blocked. It is never itself a certification.
keys=()
# Verify every evidence ID in the file, including optional self-assessment or drill records, not only required IDs.
while IFS='=' read -r k v; do
  [[ -z "$k" || "$k" == \#* ]] && continue
  [[ "$k" == *_EVIDENCE_ID || "$k" == *_RESULT_ID ]] && keys+=("$v")
done < "$FILE"

# Optional upload/remote verification phase. Publishing is forbidden unless this phase completes.
if [[ "${UPLOAD_EVIDENCE:-no}" == yes ]]; then
  [[ -n "${R2_BUCKET:-}" && -n "${R2_ENDPOINT_URL:-}" ]] || fail "Set R2_BUCKET and R2_ENDPOINT_URL"
  command -v aws >/dev/null 2>&1 || fail "AWS CLI is required for R2 evidence upload/verification"
  [[ "${R2_ENDPOINT_URL}" == https://* ]] || fail "R2_ENDPOINT_URL must be HTTPS"
  AWS_PROFILE="${AWS_PROFILE:-neelastack-r2}"
  WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
  awsx() { aws --profile "$AWS_PROFILE" --endpoint-url "$R2_ENDPOINT_URL" "$@"; }
  # A key is always a bucket-relative object key under the fixed readiness prefix.
  ensure_object() {
    local key="$1" local_file="$2" remote="$3" remote_sum="$4" tmp="$WORK/$(basename "$key")"
    [[ "$key" =~ ^production/readiness-evidence/[A-Za-z0-9._/-]+$ && "$key" != *..* ]] || fail "Evidence ID is not a safe R2 key: $key"
    if [[ -f "$local_file" ]]; then
      [[ -f "$local_file.sha256" ]] || fail "Local checksum sidecar missing: $local_file.sha256"
      ( cd "$(dirname "$local_file")" && sha256sum -c "$(basename "$local_file").sha256" >/dev/null ) || fail "Local checksum verification failed: $local_file"
      if awsx s3api head-object --bucket "$R2_BUCKET" --key "$key" >/dev/null 2>&1; then
        awsx s3 cp "s3://$R2_BUCKET/$key" "$tmp" >/dev/null || fail "Cannot read existing locked R2 object: $key"
        cmp -s "$local_file" "$tmp" || fail "R2 object already exists with different bytes; refusing overwrite: $key"
      else
        awsx s3 cp "$local_file" "s3://$R2_BUCKET/$key" >/dev/null || fail "Upload failed for $key"
      fi
      # Upload/verify the sidecar independently; bucket lock may reject overwrites, so only create absent keys.
      if awsx s3api head-object --bucket "$R2_BUCKET" --key "$key.sha256" >/dev/null 2>&1; then
        awsx s3 cp "s3://$R2_BUCKET/$key.sha256" "$remote_sum" >/dev/null || fail "Cannot read checksum sidecar for $key"
        cmp -s "$local_file.sha256" "$remote_sum" || fail "R2 checksum sidecar differs for $key; refusing overwrite"
      else
        awsx s3 cp "$local_file.sha256" "s3://$R2_BUCKET/$key.sha256" >/dev/null || fail "Checksum sidecar upload failed for $key"
      fi
    else
      awsx s3 cp "s3://$R2_BUCKET/$key" "$tmp" >/dev/null || fail "Evidence object absent locally and cannot be downloaded from R2: $key"
      awsx s3 cp "s3://$R2_BUCKET/$key.sha256" "$remote_sum" >/dev/null || fail "R2 checksum sidecar missing: $key.sha256"
      cp "$remote_sum" "$tmp.sha256"
      ( cd "$WORK" && sha256sum -c "$(basename "$tmp").sha256" >/dev/null ) || fail "Remote checksum verification failed: $key"
    fi
    # Always download the final object and sidecar and verify bytes, including fresh uploads.
    awsx s3 cp "s3://$R2_BUCKET/$key" "$tmp" >/dev/null || fail "Cannot read back R2 object: $key"
    awsx s3 cp "s3://$R2_BUCKET/$key.sha256" "$remote_sum" >/dev/null || fail "Cannot read back R2 sidecar: $key.sha256"
    cp "$remote_sum" "$tmp.sha256"
    ( cd "$WORK" && sha256sum -c "$(basename "$tmp").sha256" >/dev/null ) || fail "R2 read-back checksum failed: $key"
    echo "R2 verified: $key"
  }
  for key in "${keys[@]}"; do
    local_file="$EVIDENCE_DIR/$(basename "$key")"
    ensure_object "$key" "$local_file" "$WORK/$(basename "$key").download" "$WORK/$(basename "$key").remote.sha256"
  done
else
  echo "NOTICE: R2 objects/checksums have not been verified in this run. This is validation-only dry run."
fi

if [[ "${APPLY:-no}" == yes ]]; then
  (( missing == 0 )) || fail "Refusing GitHub variable publication while readiness is incomplete: ${missing_reasons[*]}"
  [[ "${UPLOAD_EVIDENCE:-no}" == yes ]] || fail "Refusing GitHub publication: set UPLOAD_EVIDENCE=yes and verify every report in R2 first"
  command -v gh >/dev/null 2>&1 || fail "GitHub CLI (gh) is required; install it or add variables manually in GitHub settings"
  gh auth status >/dev/null 2>&1 || fail "GitHub CLI is not authenticated"
  echo "Publishing verified variables to environment '$ENVNAME' in '$REPO'..."
  while IFS='=' read -r k v; do
    [[ -z "$k" || "$k" == \#* ]] && continue
    [[ "$k" =~ ^[A-Z0-9_]+$ ]] || fail "Invalid variable name in $FILE: $k"
    if [[ "$k" == *_EVIDENCE_ID || "$k" == *_RESULT_ID ]]; then
      [[ "$v" =~ ^production/readiness-evidence/[A-Za-z0-9._/-]+$ && "$v" != *..* ]] || fail "Unsafe evidence ID for $k: $v"
    fi
    gh variable set "$k" --repo "$REPO" --env "$ENVNAME" --body "$v" && echo "set $k"
  done < "$FILE"
else
  echo "Validated variable file: $FILE"
  echo "Target environment: $ENVNAME | Repository: $REPO"
  echo "Values are NOT published (APPLY is not yes)."
  if [[ "${UPLOAD_EVIDENCE:-no}" == yes ]]; then echo "R2 upload/read-back verification completed. Re-run with APPLY=yes only after reviewing the variable file."; fi
  if (( missing > 0 )); then
    echo "Readiness is BLOCKED for: ${missing_reasons[*]}"
    echo "This did not publish certification variables. The read-only R2 aggregator will reject missing/failed evidence."
  fi
fi
