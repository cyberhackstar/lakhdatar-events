#!/usr/bin/env bash
set -euo pipefail

SCRIPT="infra/readiness/run-payment-chaos-staging.ps1"
[[ -s "$SCRIPT" ]] || { echo "missing staging payment-chaos runner: $SCRIPT" >&2; exit 1; }

if LC_ALL=C grep -n '[^ -~]' "$SCRIPT"; then
  echo "PowerShell runner must remain ASCII-safe for Windows PowerShell 5.1 without a UTF-8 BOM." >&2
  exit 1
fi

# Prevent accidental duplicated PowerShell script bodies. Static parsing alone can pass a duplicated file.
for declaration in \
  'function Stop-Safely([string]$Message) { throw $Message }' \
  'function Upload-VerifiedEvidenceToR2([string]$ArtifactPath, [string]$EvidenceId) {'; do
  count="$(grep -Fxc -- "$declaration" "$SCRIPT" || true)"
  [[ "$count" == "1" ]] || {
    echo "PowerShell runner must contain exactly one declaration of: $declaration (found $count)." >&2
    exit 1
  }
done
[[ "$(wc -l < "$SCRIPT" | tr -d '[:space:]')" -lt 700 ]] || {
  echo "PowerShell runner is unexpectedly long; refusing a duplicated-body regression." >&2
  exit 1
}

require_literal() {
  local needle="$1"
  grep -Fq -- "$needle" "$SCRIPT" || {
    echo "missing required staging safety contract: $needle" >&2
    exit 1
  }
}
reject_literal() {
  local needle="$1"
  if grep -Fq -- "$needle" "$SCRIPT"; then
    echo "forbidden action in staging payment-chaos runner: $needle" >&2
    exit 1
  fi
}

# Fixed-origin, staging-only, exact-release identity.
require_literal "https://staging-events.neelastack.com"
require_literal 'function ConvertTo-UnixLineEndings([string]$Text)'
require_literal '$normalizedScript = ConvertTo-UnixLineEndings -Text $ScriptText'
require_literal '$normalizedRemote = ConvertTo-UnixLineEndings -Text $remote'
require_literal 'if ($StagingUrl -ne $ExpectedStagingUrl)'
require_literal "/home/ubuntu/apps/lakhdatar-events-staging"
require_literal "ROOT='/home/ubuntu/apps/lakhdatar-events-staging'"
require_literal "staging root guard failed"
require_literal 'APP_ENV staging'
require_literal 'SPRING_PROFILES_ACTIVE staging'
require_literal 'DEFAULT_PAYMENT_PROVIDER CASHFREE'
require_literal 'CASHFREE_BASE_URL https://sandbox.cashfree.com/pg'
require_literal 'CASHFREE_API_VERSION 2025-01-01'
require_literal 'DEPLOYED_SHA=$sha'
require_literal 'release-qualification-$ReleaseSha'
require_literal "Release qualification result (not production certification)"

# Container and data-plane isolation contracts.
require_literal 'lakhdatar-events-staging'
require_literal 'lakhdatar_pg_data_staging'
require_literal 'lakhdatar_redis_data_staging'
require_literal 'lakhdatar-staging-postgres'
require_literal 'lakhdatar-staging-redis'
require_literal 'lakhdatar-staging-backend'
require_literal 'lakhdatar-staging-web'
require_literal 'lakhdatar-staging-edge'
require_literal 'docker exec lakhdatar-staging-postgres pg_isready'
require_literal 'http://127.0.0.1:4003/edge-health'

# Restarts need exact interactive confirmations and verify post-restart readiness.
require_literal "RESTART STAGING POSTGRES"
require_literal "RESTART STAGING REDIS"
require_literal 'docker restart --time 30 "$CONTAINER"'
require_literal 'STAGING_RESTART=PASS'

# All nine scenarios, separate c10 sandbox-only confirmation, real observation, and evidence gates.
for scenario in c1 c2 c3 c4 c5 c6 c7 c8 c9; do
  require_literal "Id='$scenario'"
done
require_literal "c10=yes:all scenarios completed on staging with sandbox/test payment credentials only"
require_literal 'Observed result: P = expected behavior verified, F = failed, S = skipped'
require_literal 'Operator-reported outcome: $Outcome'
require_literal '$answerLines | Set-Content -LiteralPath $answerFile -Encoding Ascii'
require_literal 'UTF-8 BOM emitted by Windows PowerShell 5.1'
require_literal 'function Test-SafeEvidenceText'
require_literal 'Observation rejected because it appears to contain personal data or secret material'
require_literal 'CASHFREE_API_VERSION 2025-01-01'
require_literal 'temporary path validation failed'
require_literal 'Any upload below publishes a FAIL/blocked record; it does not turn the run into a pass.'
require_literal 'To upload the failed evidence bundle and FAIL result JSON to R2 and verify read-back'
require_literal 'Latest payment-chaos result is FAIL:'
require_literal 'FAIL evidence and result JSON read-back verified. This published record keeps readiness blocked.'
require_literal 'No completed PAYMENT_CHAOS evidence/result IDs exist. This appears to be a preflight/collection failure; no R2 upload was attempted.'
require_literal 'R2_UPLOAD_READBACK=PASS'
require_literal "[string]$EvidenceBucket = 'neelastack-readiness-evidence'"
require_literal "BUCKET='__EVIDENCE_BUCKET__'"
require_literal ".Replace('__EVIDENCE_BUCKET__', $EvidenceBucket)"
require_literal "awsx() { aws --profile neelastack-r2-evidence --endpoint-url"
require_literal 'Refusing to store readiness evidence in the production backup bucket'
require_literal 'awsx s3api head-object --bucket "$BUCKET" --key "$KEY"'
require_literal 'R2 upload refused: immutable evidence object already exists'
require_literal 'sha256sum -c'
require_literal 'To upload the evidence bundle and its result JSON to R2 and verify read-back'
require_literal '$uploadConfirm = Read-Host $uploadPrompt'
require_literal "^PAYMENT_CHAOS_RESULT_ID="
require_literal '$resultLine = Get-Content -LiteralPath $variablesFile'
require_literal 'Candidate payment-chaos result ID:'
require_literal 'The machine-readable result JSON is missing; refusing upload.'
require_literal 'Upload-VerifiedEvidenceToR2 -ArtifactPath $resultPath -EvidenceId $resultId'
require_literal 'Latest payment-chaos result is FAIL:'
require_literal 'To upload the failed evidence bundle and FAIL result JSON to R2 and verify read-back'
require_literal 'UPLOAD FAILED PAYMENT CHAOS EVIDENCE'
require_literal 'This published record keeps readiness blocked.'
require_literal 'preflight/collection failure'

# No route is allowed to mutate production or publish certification variables.
reject_literal 'docker compose down'
reject_literal 'docker volume rm'
reject_literal 'docker system prune'
reject_literal 'gh variable set'
reject_literal 'ENTERPRISE_CERTIFIED_SHA'
reject_literal 'PRODUCTION_DB_HOST='
reject_literal "BUCKET='neelastack-prod-db-backups'"

echo "PASS: staging payment-chaos runner safety contracts."
