# Single-node readiness evidence inventory (read-only)
# Run from Windows PowerShell in the Lakhdatar Events repo.
# Requires OpenSSH and an SSH alias named `vm` that logs into the production VM.
# This lists existing candidate evidence in R2 and ops-snapshots. It does NOT
# modify production, create attestations, or set any readiness status to PASS.

$ErrorActionPreference = 'Stop'

if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    throw 'OpenSSH client (ssh.exe) was not found. Enable/install Windows OpenSSH Client, then rerun.'
}

$remote = @'
set -u
BUCKET='neelastack-prod-db-backups'
PROFILE='neelastack-r2'
ENDPOINT='https://058bc80c5f25ec698f8aa691f0750603.r2.cloudflarestorage.com'
PREFIX='production/readiness-evidence/'

printf '\n=== VM / tool availability ===\n'
printf 'UTC time: '; date -u +%FT%TZ
if ! command -v aws >/dev/null 2>&1; then
  echo 'AWS_CLI=NOT_FOUND'
  exit 2
fi
TMP_R2=$(mktemp)
trap 'rm -f "$TMP_R2"' EXIT
if aws s3 ls "s3://$BUCKET/$PREFIX" --recursive --profile "$PROFILE" --endpoint-url "$ENDPOINT" >"$TMP_R2" 2>&1; then
  echo 'R2_LIST=PASS'
else
  echo 'R2_LIST=FAIL (see error below; no credentials are printed)'
  sed -E 's/(SecretAccessKey|AccessKeyId|session token|token)[=: ]+[^ ,]+/\1=[REDACTED]/Ig' "$TMP_R2" | tail -n 8
  exit 3
fi

show_r2_matches() {
  label="$1"
  regex="$2"
  echo
  echo "=== R2 candidate evidence: $label ==="
  matches=$(awk 'NF >= 4 {print $4}' "$TMP_R2" | grep -Ei "$regex" || true)
  if [ -z "$matches" ]; then
    echo 'NO_MATCHING_R2_OBJECTS'
  else
    printf '%s\n' "$matches" | while IFS= read -r key; do
      case "$key" in
        production/readiness-evidence/*) printf '%s\n' "$key" ;;
        *) printf 'production/readiness-evidence/%s\n' "$key" ;;
      esac
    done
  fi
}

show_r2_matches 'PAYMENT / CHAOS' 'payment|cashfree|chaos|refund|provider'
show_r2_matches 'PEN TEST / SECURITY' 'pen.?test|penetrat|security|dast|zap|vulnerability'
show_r2_matches 'FINANCE / RECONCILIATION' 'finance|reconcil|ledger|settlement|refund'
show_r2_matches 'PRIVACY / LEGAL' 'privacy|data.?governance|legal|terms|retention'
show_r2_matches 'PRIVILEGED MFA' 'mfa|privileged|2fa|totp|authenticator'

printf '\n=== Local retained reports ===\n'
found=0
for root in /home/ubuntu/ops-snapshots /home/ubuntu/apps/lakhdatar-events/certification-evidence /home/ubuntu/apps/lakhdatar-events/ops-snapshots; do
  if [ -d "$root" ]; then
    found=1
    find "$root" -type f \( -iname '*.txt' -o -iname '*.md' -o -iname '*.json' -o -iname '*.csv' -o -iname '*.log' \) \
      -printf '%TY-%Tm-%TdT%TH:%TM:%TSZ\t%s bytes\t%p\n' 2>/dev/null \
      | grep -Ei 'payment|cashfree|chaos|refund|reconcil|finance|ledger|settlement|pen.?test|penetrat|security|dast|zap|privacy|legal|retention|mfa|privileged|2fa|totp|alert|backup|recovery|pitr' \
      | sort -r | head -n 250 || true
  fi
done
if [ "$found" = 0 ]; then
  echo 'NO_KNOWN_LOCAL_EVIDENCE_DIRECTORIES_FOUND'
fi

printf '\n=== Important ===\n'
echo 'These are filename candidates only. Open and verify each report and checksum before using it as an evidence ID.'
echo 'No production config, database, containers, secrets, or GitHub variables were changed.'
'@

$bytes = [System.Text.Encoding]::UTF8.GetBytes($remote)
$b64 = [Convert]::ToBase64String($bytes)
Write-Host 'Connecting to SSH alias vm; this inventory is read-only...' -ForegroundColor Cyan
& ssh vm "printf '%s' '$b64' | base64 -d | bash"
if ($LASTEXITCODE -ne 0) {
    throw "Remote evidence inventory failed with exit code $LASTEXITCODE. Review the error above; nothing was modified."
}
