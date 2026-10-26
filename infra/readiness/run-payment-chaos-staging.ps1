[CmdletBinding()]
param(
    [string]$StagingUrl = 'https://staging-events.neelastack.com',
    [string]$SshAlias = 'vm',
    [string]$EvidenceRoot = (Join-Path $env:LOCALAPPDATA 'Neelastack\payment-chaos-evidence'),
    [string]$EvidenceBucket = 'neelastack-readiness-evidence',
    [switch]$PreflightOnly,
    [ValidateRange(0, 60)]
    [int]$RecoveryWaitMinutes = 15
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ExpectedStagingUrl = 'https://staging-events.neelastack.com'
if ($EvidenceBucket -notmatch '^[a-z0-9][a-z0-9-]{2,62}

function Stop-Safely([string]$Message) { throw $Message }

function ConvertTo-UnixLineEndings([string]$Text) {
    return $Text.Replace("`r`n", "`n").Replace("`r", "`n")
}

function Test-SafeEvidenceText([string]$Value) {
    if ($Value -match '(?i)\b(password|secret|bearer|authorization|api[_ -]?key|access[_ -]?key|mfa.?seed|private.?key)\s*[:=]\s*\S+') { return $false }
    if ($Value -match '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b') { return $false }
    if ($Value -match '\b\d{13,19}\b') { return $false }
    if ($Value -match '-----BEGIN [A-Z ]*PRIVATE KEY-----') { return $false }
    return $true
}

function Get-GitBash {
    $bash = Get-Command bash.exe -ErrorAction SilentlyContinue
    if (-not $bash) { $bash = Get-Command bash -ErrorAction SilentlyContinue }
    if (-not $bash) {
        $git = Get-Command git.exe -ErrorAction SilentlyContinue
        if (-not $git) { $git = Get-Command git -ErrorAction SilentlyContinue }
        if ($git) {
            $gitRoot = Split-Path (Split-Path $git.Source -Parent) -Parent
            $candidate = Join-Path $gitRoot 'bin\bash.exe'
            if (Test-Path $candidate) { $bash = [pscustomobject]@{ Source = $candidate } }
        }
    }
    if (-not $bash) { Stop-Safely 'Git Bash was not found. Install Git for Windows, then rerun this script.' }
    return $bash
}

function ConvertTo-BashPath([string]$Path) {
    $bash = Get-GitBash
    $literal = $Path.Replace("'", "'\''")
    $converted = @(& $bash.Source -lc "cygpath -u '$literal'" 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $converted) { Stop-Safely 'Could not safely convert a Windows path for the readiness runner.' }
    return ([string]$converted[-1]).Trim()
}

function Invoke-RemoteBash([string]$ScriptText) {
    $ssh = Get-Command ssh.exe -ErrorAction SilentlyContinue
    if (-not $ssh) { $ssh = Get-Command ssh -ErrorAction SilentlyContinue }
    if (-not $ssh) { Stop-Safely 'OpenSSH client was not found.' }
    $normalizedScript = ConvertTo-UnixLineEndings -Text $ScriptText
    $encoded = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($normalizedScript))
    $command = "printf '%s' '$encoded' | base64 -d | bash"
    $output = @(& $ssh.Source -o BatchMode=yes -o ConnectTimeout=15 $SshAlias $command 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        foreach ($line in $output) { Write-Host ([string]$line) }
        Stop-Safely "Staging-scoped SSH operation failed (exit $exitCode). Secret values were not requested or printed."
    }
    return $output
}

function Test-LocalRepository {
    $rootOutput = @(& git rev-parse --show-toplevel 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $rootOutput) { Stop-Safely 'Run this script from inside the Lakhdatar Events Git repository.' }
    $root = ([string]$rootOutput[0]).Trim()
    if ((Resolve-Path '.').Path.TrimEnd('\') -ne (Resolve-Path $root).Path.TrimEnd('\')) {
        Stop-Safely 'Run this script from the repository root.'
    }
    $branch = ([string](& git branch --show-current)).Trim()
    if ($branch -ne 'main') { Stop-Safely 'Switch to main and pull the reviewed automation before running evidence collection.' }
    & git diff --quiet --no-ext-diff
    if ($LASTEXITCODE -ne 0) { Stop-Safely 'Tracked working-tree edits detected. Commit or stash them before running readiness evidence.' }
    & git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) { Stop-Safely 'Staged edits detected. Commit or unstage them before running readiness evidence.' }
    foreach ($path in @('.github/workflows/enterprise-release-qualification.yml', 'infra/readiness/payment-chaos.sh', 'infra/readiness/finance-invariants.sql')) {
        if (-not (Test-Path (Join-Path $root $path))) { Stop-Safely "Required reviewed file is missing: $path" }
    }
    return $root
}

function Get-VerifiedStagingPreflight {
    $remote = @'
set -euo pipefail
ROOT='/home/ubuntu/apps/lakhdatar-events-staging'
fail() { printf 'STAGING_PREFLIGHT=FAIL (%s)\n' "$1" >&2; exit 2; }
[[ "$ROOT" == '/home/ubuntu/apps/lakhdatar-events-staging' ]] || fail 'staging root guard failed'
cd "$ROOT" || fail 'staging root not found'
test -s .env || fail 'staging environment file missing'
test "$(stat -c %a .env 2>/dev/null)" = '600' || fail 'staging environment permissions must be 0600'
test -s .deploy-current || fail 'staging release marker missing'
sha="$(tr -d '\r\n' < .deploy-current)"
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || fail 'staging release marker invalid'

must_env() {
  local key="$1" expected="$2" value
  value="$(grep -E "^${key}=" .env | tail -n 1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")"
  [[ "$value" == "$expected" ]] || fail "staging config guard failed for $key"
}
must_present_nonplaceholder() {
  key="$1"; minimum="$2"
  awk -v key="$key" -v minimum="$minimum" '
    index($0,key "=")==1 { value=substr($0,length(key)+2) }
    END {
      if (value ~ /^".*"$/ || value ~ /^'\''.*'\''$/) value=substr(value,2,length(value)-2)
      if (length(value) < minimum || value ~ /^(<|replace-with-|change-me|REPLACE_WITH)/) exit 1
    }' .env || fail "required staging value is missing or placeholder: $key"
}
must_env APP_ENV staging
must_env PUBLIC_BASE_URL https://staging-events.neelastack.com
must_env CORS_ALLOWED_ORIGINS https://staging-events.neelastack.com
# Verify settings from the actual running container below; Compose supplies safe defaults for these optional .env entries.
must_present_nonplaceholder CASHFREE_APP_ID 4
must_present_nonplaceholder CASHFREE_SECRET_KEY 8
must_present_nonplaceholder DB_PASSWORD 12
must_present_nonplaceholder REDIS_PASSWORD 32
must_present_nonplaceholder JWT_SECRET 32
must_present_nonplaceholder TICKET_VIEW_SECRET 32
must_present_nonplaceholder QR_SIGNING_SECRET 32
must_present_nonplaceholder MFA_ENCRYPTION_KEY 32
if grep -Eq 'https://events\.neelastack\.com|^CASHFREE_BASE_URL=https://api\.cashfree\.com/pg$' .env; then fail 'production origin/provider endpoint detected in staging config'; fi

container_check() {
  container="$1"; service="$2"
  state="$(docker inspect --format '{{.State.Status}}' "$container" 2>/dev/null || true)"
  project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$container" 2>/dev/null || true)"
  actual_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$container" 2>/dev/null || true)"
  [[ "$state" == running && "$project" == lakhdatar-events-staging && "$actual_service" == "$service" ]] || fail "container guard failed for $container"
}
container_check lakhdatar-staging-postgres postgres
container_check lakhdatar-staging-redis redis
container_check lakhdatar-staging-backend backend
container_check lakhdatar-staging-web web
container_check lakhdatar-staging-edge edge

runtime_must() {
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' lakhdatar-staging-backend 2>/dev/null | grep -Fxq "$1=$2" || fail "backend runtime sandbox guard failed for $1"
}
runtime_must APP_ENV staging
runtime_must SPRING_PROFILES_ACTIVE staging
runtime_must DB_URL jdbc:postgresql://postgres:5432/lakhdatar_staging
runtime_must REDIS_HOST redis
runtime_must DEFAULT_PAYMENT_PROVIDER CASHFREE
runtime_must CASHFREE_BASE_URL https://sandbox.cashfree.com/pg
runtime_must CASHFREE_API_VERSION 2025-01-01

for service in backend web edge; do
  case "$service" in
    backend) container='lakhdatar-staging-backend' ;;
    web) container='lakhdatar-staging-web' ;;
    edge) container='lakhdatar-staging-edge' ;;
  esac
  image="$(docker inspect --format '{{.Config.Image}}' "$container")"
  [[ "$image" == *":$sha" ]] || fail "$service image tag differs from the staging release marker"
done
docker inspect --format '{{range .Mounts}}{{println .Name}}{{end}}' lakhdatar-staging-postgres | grep -qx 'lakhdatar_pg_data_staging' || fail 'staging PostgreSQL volume mismatch'
docker inspect --format '{{range .Mounts}}{{println .Name}}{{end}}' lakhdatar-staging-redis | grep -qx 'lakhdatar_redis_data_staging' || fail 'staging Redis volume mismatch'
docker exec lakhdatar-staging-postgres pg_isready -U lakhdatar -d lakhdatar_staging >/dev/null 2>&1 || fail 'staging PostgreSQL is not ready'
docker exec lakhdatar-staging-backend wget -qO- --timeout=5 http://127.0.0.1:8081/actuator/health/readiness 2>/dev/null | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || fail 'staging backend readiness failed'
curl -fsS --max-time 8 http://127.0.0.1:4003/edge-health >/dev/null || fail 'staging edge health failed'
echo 'STAGING_PREFLIGHT=PASS'
echo "DEPLOYED_SHA=$sha"
echo 'STAGING_PROVIDER=CASHFREE_SANDBOX'
echo 'STAGING_CONTAINERS=PASS'
echo 'STAGING_DATABASE_AND_EDGE=PASS'
'@
    return Invoke-RemoteBash -ScriptText $remote
}

function Find-QualificationForRelease([string]$ReleaseSha) {
    $headers = @{ Accept = 'application/vnd.github+json'; 'User-Agent' = 'Neelastack-Payment-Chaos-Runner'; 'X-GitHub-Api-Version' = '2022-11-28' }
    try {
        $response = Invoke-RestMethod -Method Get -Uri "$ApiRoot/actions/runs?branch=main&per_page=100" -Headers $headers -TimeoutSec 20
    } catch { Stop-Safely 'Could not query public GitHub Actions metadata. Check internet access and retry.' }
    $candidates = @($response.workflow_runs | Where-Object {
        $_.name -eq 'Enterprise Release Qualification' -and $_.status -eq 'completed' -and $_.conclusion -eq 'success' -and $_.head_branch -eq 'main'
    } | Sort-Object { [datetime]$_.updated_at } -Descending | Select-Object -First 30)
    $expectedArtifact = "release-qualification-$ReleaseSha"
    foreach ($run in $candidates) {
        try {
            $artifacts = Invoke-RestMethod -Method Get -Uri "$ApiRoot/actions/runs/$($run.id)/artifacts" -Headers $headers -TimeoutSec 20
            if (-not @($artifacts.artifacts | Where-Object { $_.name -eq $expectedArtifact -and -not $_.expired }).Count) { continue }
            $jobsResponse = Invoke-RestMethod -Method Get -Uri "$ApiRoot/actions/runs/$($run.id)/jobs?per_page=100" -Headers $headers -TimeoutSec 20
            $requiredJobs = @('browser-e2e', 'Verify requested SHA is deployed to staging', 'Release qualification result (not production certification)', 'database', 'dast', 'load')
            $allPassed = $true
            foreach ($jobName in $requiredJobs) {
                $job = $jobsResponse.jobs | Where-Object { $_.name -eq $jobName } | Select-Object -First 1
                if (-not $job -or $job.conclusion -ne 'success') { $allPassed = $false; break }
            }
            if ($allPassed) {
                return [pscustomobject]@{
                    ReleaseSha = $ReleaseSha
                    RunId = [string]$run.id
                    RunUrl = [string]$run.html_url
                    UpdatedAt = [string]$run.updated_at
                    ArtifactName = $expectedArtifact
                }
            }
        } catch { continue }
    }
    Stop-Safely "No successful qualification artifact with required passing jobs was found for staging release $ReleaseSha. Requalify the exact release before collecting payment-chaos evidence."
}

function Invoke-StagingRestart([ValidateSet('postgres', 'redis')][string]$Service, [string]$ExpectedSha) {
    $remote = @'
set -euo pipefail
ROOT='/home/ubuntu/apps/lakhdatar-events-staging'
cd "$ROOT" || { echo 'Restart refused: staging root missing' >&2; exit 2; }
EXPECTED_SHA='__EXPECTED_SHA__'
SERVICE='__SERVICE__'
CURRENT="$(tr -d '\r\n' < .deploy-current)"
[[ "$CURRENT" == "$EXPECTED_SHA" ]] || { echo 'Restart refused: staging release marker changed since preflight' >&2; exit 2; }
case "$SERVICE" in
  postgres) CONTAINER='lakhdatar-staging-postgres'; VOLUME='lakhdatar_pg_data_staging' ;;
  redis) CONTAINER='lakhdatar-staging-redis'; VOLUME='lakhdatar_redis_data_staging' ;;
  *) echo 'Restart refused: service is not allowlisted' >&2; exit 2 ;;
esac
PROJECT="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$CONTAINER" 2>/dev/null || true)"
ACTUAL_SERVICE="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$CONTAINER" 2>/dev/null || true)"
[[ "$PROJECT" == lakhdatar-events-staging && "$ACTUAL_SERVICE" == "$SERVICE" ]] || { echo 'Restart refused: Compose project/service identity mismatch' >&2; exit 2; }
docker inspect --format '{{range .Mounts}}{{println .Name}}{{end}}' "$CONTAINER" | grep -qx "$VOLUME" || { echo 'Restart refused: staging data volume mismatch' >&2; exit 2; }
STARTED="$(date -u +%FT%TZ)"
docker restart --time 30 "$CONTAINER" >/dev/null
ready=0
for attempt in $(seq 1 60); do
  state="$(docker inspect --format '{{.State.Status}}' "$CONTAINER" 2>/dev/null || true)"
  if [[ "$state" == running ]]; then
    if [[ "$SERVICE" == postgres ]]; then
      if docker exec lakhdatar-staging-postgres pg_isready -U lakhdatar -d lakhdatar_staging >/dev/null 2>&1; then ready=1; break; fi
    else
      health="$(docker inspect --format '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || true)"
      if [[ "$health" == healthy ]]; then ready=1; break; fi
    fi
  fi
  sleep 2
done
[[ "$ready" == 1 ]] || { echo 'Staging service did not reach readiness in 120 seconds; stop and investigate staging' >&2; exit 3; }
for attempt in $(seq 1 60); do
  backend="$(docker exec lakhdatar-staging-backend wget -qO- --timeout=4 http://127.0.0.1:8081/actuator/health/readiness 2>/dev/null || true)"
  edge="$(curl -fsS --max-time 4 http://127.0.0.1:4003/edge-health >/dev/null 2>&1 && echo UP || true)"
  if [[ "$backend" == *'"status":"UP"'* && "$edge" == UP ]]; then
    echo 'STAGING_RESTART=PASS'
    echo "RESTARTED_SERVICE=$SERVICE"
    echo "RESTART_START_UTC=$STARTED"
    echo "RESTART_FINISH_UTC=$(date -u +%FT%TZ)"
    exit 0
  fi
  sleep 2
done
echo 'Container restarted but backend/edge readiness did not recover in 120 seconds; stop and investigate staging' >&2
exit 4
'@
    $remote = $remote.Replace('__EXPECTED_SHA__', $ExpectedSha).Replace('__SERVICE__', $Service)
    return Invoke-RemoteBash -ScriptText $remote
}

function Get-ScenarioPlan {
    return @(
        [pscustomobject]@{ Id='c1'; Title='Browser closes after checkout creation'; Instruction='Use a disposable Cashfree sandbox checkout. Close before paying, open /recover, use the same order number and checkout email, resume the same provider order, complete sandbox payment, then verify exactly one ticket set.' },
        [pscustomobject]@{ Id='c2'; Title='Duplicate payment verification'; Instruction='For a disposable sandbox-paid order, repeat the verification/return request twice and verify one confirmation plus one ticket set.' },
        [pscustomobject]@{ Id='c3'; Title='Webhook before browser verification'; Instruction='Complete a sandbox payment and close the browser immediately. Verify the webhook settles the order once, then open the ticket link.' },
        [pscustomobject]@{ Id='c4'; Title='Duplicate and out-of-order webhooks'; Instruction='Use Cashfree sandbox tools to resend the same event repeatedly and out of order. Verify no duplicate tickets, ledger entries, or emails.' },
        [pscustomobject]@{ Id='c5'; Title='Provider outage during refund'; Instruction='Use a previously reviewed staging-only provider fault method. This runner will not edit .env or firewall rules. If no isolated method is available, mark SKIP.' },
        [pscustomobject]@{ Id='c6'; Title='Refund failure followed by recovery'; Instruction='Use a disposable sandbox-paid order and approved fault/recovery method. Verify recovery and exactly one REFUND ledger entry.' },
        [pscustomobject]@{ Id='c7'; Title='Event cancellation during refund recovery'; Instruction='Use disposable sandbox-paid orders and approved isolated fault injection; cancel the test event, restore provider access, and verify refunds or recorded manual-review outcomes.' },
        [pscustomobject]@{ Id='c8'; Title='PostgreSQL restart during payment'; Instruction='Time this step immediately after sandbox payment success while webhook/application processing is in flight. The runner can restart only staging PostgreSQL after exact confirmation; then verify consistent order state.' },
        [pscustomobject]@{ Id='c9'; Title='Redis restart during checkout load'; Instruction='Start disposable staging checkout/recovery traffic in another window. The runner can restart only staging Redis after exact confirmation; then verify checkout/recovery remain healthy.' }
    )
}

function Write-ScenarioEvidence {
    param(
        [string]$RunDirectory, $Scenario, [string]$Operator, [string]$ReleaseSha,
        [string]$QualificationUrl, [string]$Outcome, [string]$Observation,
        [string]$OrderReference, [string]$RestartLog
    )
    @(
        "Scenario: $($Scenario.Id) - $($Scenario.Title)"
        "Recorded UTC: $([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))"
        "Operator: $Operator"
        "Staging URL: $ExpectedStagingUrl"
        "Application release SHA: $ReleaseSha"
        "Passing qualification run: $QualificationUrl"
        "Operator-reported outcome: $Outcome"
        "Disposable order reference: $OrderReference"
        "Observed outcome: $Observation"
        "Staging restart record: $RestartLog"
        'Do not add credentials, bearer tokens, MFA seeds, payment details, or customer PII.'
    ) | Set-Content -LiteralPath (Join-Path $RunDirectory "$($Scenario.Id).txt") -Encoding UTF8
}

function Upload-VerifiedEvidenceToR2([string]$ArtifactPath, [string]$EvidenceId) {
    if ($EvidenceId -notmatch '^production/readiness-evidence/[A-Za-z0-9._/-]+$' -or $EvidenceId.Contains('..')) {
        Stop-Safely 'Refusing R2 upload: evidence ID is outside the protected readiness prefix.'
    }
    $sidecar = "$ArtifactPath.sha256"
    if (-not (Test-Path -LiteralPath $ArtifactPath) -or -not (Test-Path -LiteralPath $sidecar)) {
        Stop-Safely 'R2 upload refused because the local bundle or SHA-256 sidecar is missing.'
    }
    $expected = ((Get-Content -LiteralPath $sidecar -TotalCount 1).Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries))[0]
    $actual = (Get-FileHash -LiteralPath $ArtifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $expected -or $actual -ne $expected.ToLowerInvariant()) { Stop-Safely 'R2 upload refused because the local checksum does not match.' }
    $ssh = Get-Command ssh.exe -ErrorAction SilentlyContinue
    if (-not $ssh) { $ssh = Get-Command ssh -ErrorAction SilentlyContinue }
    $scp = Get-Command scp.exe -ErrorAction SilentlyContinue
    if (-not $scp) { $scp = Get-Command scp -ErrorAction SilentlyContinue }
    if (-not $ssh -or -not $scp) { Stop-Safely 'OpenSSH ssh/scp clients were not found; evidence was not uploaded.' }

    $token = [Guid]::NewGuid().ToString('N')
    $remoteDir = "/tmp/neelastack-payment-chaos-$token"
    $artifactName = [System.IO.Path]::GetFileName($ArtifactPath)
    $remoteData = "$remoteDir/$artifactName"
    $remoteSum = "$remoteData.sha256"
    & $ssh.Source -o BatchMode=yes -o ConnectTimeout=15 $SshAlias "install -d -m 700 '$remoteDir'" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Stop-Safely 'Could not create a private temporary upload directory; R2 upload was not attempted.' }
    & $scp.Source -o BatchMode=yes -o ConnectTimeout=15 $ArtifactPath "$($SshAlias):$remoteData"
    if ($LASTEXITCODE -ne 0) { Stop-Safely 'SCP of evidence bundle failed; no GitHub variables were changed.' }
    & $scp.Source -o BatchMode=yes -o ConnectTimeout=15 $sidecar "$($SshAlias):$remoteSum"
    if ($LASTEXITCODE -ne 0) { Stop-Safely 'SCP of checksum sidecar failed; no GitHub variables were changed.' }

    $remote = @'
set -euo pipefail
BUCKET='__EVIDENCE_BUCKET__'
ENDPOINT='https://058bc80c5f25ec698f8aa691f0750603.r2.cloudflarestorage.com'
KEY='__KEY__'
DIR='__REMOTE_DIR__'
SRC='__REMOTE_DATA__'
SUM='__REMOTE_SUM__'
TMP="$(mktemp -d)"
cleanup() {
  if [[ "$DIR" =~ ^/tmp/neelastack-payment-chaos-[0-9a-f]{32}$ ]]; then
    rm -rf -- "$DIR"
  else
    echo 'Refusing unsafe temporary-path cleanup' >&2
  fi
  rm -rf -- "$TMP"
}
trap cleanup EXIT
[[ "$DIR" =~ ^/tmp/neelastack-payment-chaos-[0-9a-f]{32}$ && "$SRC" == "$DIR/"* && "$SUM" == "$SRC.sha256" ]] || { echo 'R2 upload refused: temporary path validation failed' >&2; exit 2; }
[[ "$KEY" =~ ^production/readiness-evidence/[A-Za-z0-9._/-]+$ && "$KEY" != *..* ]] || { echo 'R2 upload refused: unsafe evidence key' >&2; exit 2; }
awsx() { aws --profile neelastack-r2-evidence --endpoint-url "$ENDPOINT" "$@"; }
awsx s3 ls "s3://$BUCKET/production/readiness-evidence/" >/dev/null || { echo 'R2 list preflight failed; no evidence upload attempted' >&2; exit 2; }
if awsx s3api head-object --bucket "$BUCKET" --key "$KEY" >/dev/null 2>&1; then echo 'R2 upload refused: immutable evidence object already exists' >&2; exit 3; fi
if awsx s3api head-object --bucket "$BUCKET" --key "$KEY.sha256" >/dev/null 2>&1; then echo 'R2 upload refused: checksum sidecar already exists' >&2; exit 3; fi
chmod 600 "$SRC" "$SUM"
awsx s3 cp "$SRC" "s3://$BUCKET/$KEY" --no-progress >/dev/null
awsx s3 cp "$SUM" "s3://$BUCKET/$KEY.sha256" --no-progress >/dev/null
awsx s3 cp "s3://$BUCKET/$KEY" "$TMP/$(basename "$SRC")" --no-progress >/dev/null
awsx s3 cp "s3://$BUCKET/$KEY.sha256" "$TMP/$(basename "$SRC").sha256" --no-progress >/dev/null
(cd "$TMP" && sha256sum -c "$(basename "$SRC").sha256" >/dev/null)
cmp -s "$SRC" "$TMP/$(basename "$SRC")" || { echo 'R2 read-back bytes differ' >&2; exit 4; }
cmp -s "$SUM" "$TMP/$(basename "$SRC").sha256" || { echo 'R2 read-back sidecar differs' >&2; exit 4; }
echo 'R2_UPLOAD_READBACK=PASS'
echo "VERIFIED_EVIDENCE_ID=$KEY"
'@
    $remote = $remote.Replace('__KEY__', $EvidenceId).Replace('__EVIDENCE_BUCKET__', $EvidenceBucket).Replace('__REMOTE_DIR__', $remoteDir).Replace('__REMOTE_DATA__', $remoteData).Replace('__REMOTE_SUM__', $remoteSum)
    $normalizedRemote = ConvertTo-UnixLineEndings -Text $remote
    $encoded = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($normalizedRemote))
    $output = @(& $ssh.Source -o BatchMode=yes -o ConnectTimeout=15 $SshAlias "printf '%s' '$encoded' | base64 -d | bash" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        foreach ($line in $output) { Write-Host ([string]$line) }
        Stop-Safely 'R2 upload/read-back verification failed. GitHub Environment variables were not changed.'
    }
    foreach ($line in $output) { Write-Host ([string]$line) }
}

if ($StagingUrl -ne $ExpectedStagingUrl) { Stop-Safely "Refusing any origin except $ExpectedStagingUrl." }
if ($SshAlias -notmatch '^[A-Za-z0-9._-]{1,64}$') { Stop-Safely 'SSH alias must be a simple existing OpenSSH alias (for example: vm).' }
$repoRoot = Test-LocalRepository

Write-Host '=== Public HTTPS preflight ===' -ForegroundColor Cyan
foreach ($path in @('/api/v1/public/events/upcoming', '/robots.txt')) {
    try {
        $response = Invoke-WebRequest -Uri "$ExpectedStagingUrl$path" -TimeoutSec 15 -UseBasicParsing
        if ([int]$response.StatusCode -ne 200) { Stop-Safely "Staging public endpoint failed: $path" }
        Write-Host "PASS $path"
    } catch { Stop-Safely "Could not verify staging HTTPS endpoint $path. No provider action or restart was performed." }
}
try {
    $homeResponse = Invoke-WebRequest -Uri $ExpectedStagingUrl -TimeoutSec 15 -UseBasicParsing
    $hsts = @($homeResponse.Headers['Strict-Transport-Security'] | Where-Object { $_ })
    if ([int]$homeResponse.StatusCode -ne 200 -or $hsts.Count -ne 1 -or $hsts[0] -notmatch '^max-age=[1-9][0-9]*') {
        Stop-Safely 'Staging root/HSTS preflight failed.'
    }
    Write-Host 'PASS root HTTPS and single valid HSTS header'
} catch { Stop-Safely 'Could not verify staging root HTTPS/HSTS. No provider action or restart was performed.' }

Write-Host '=== Isolated staging VM preflight ===' -ForegroundColor Cyan
$preflightLines = @(Get-VerifiedStagingPreflight)
foreach ($line in $preflightLines) { Write-Host ([string]$line) }
$shaLine = $preflightLines | Where-Object { ([string]$_) -match '^DEPLOYED_SHA=[0-9a-f]{40}$' } | Select-Object -First 1
if (-not $shaLine) { Stop-Safely 'Staging preflight did not return a valid release identity.' }
$deployedSha = ([string]$shaLine) -replace '^DEPLOYED_SHA=', ''

Write-Host '=== Matching release qualification provenance ===' -ForegroundColor Cyan
$qualification = Find-QualificationForRelease -ReleaseSha $deployedSha
Write-Host "PASS qualified release SHA: $deployedSha"
Write-Host "PASS qualification run: $($qualification.RunUrl)"
Write-Host "Qualification timestamp UTC: $($qualification.UpdatedAt)"
if ($PreflightOnly) {
    Write-Host 'Preflight passed. No service was restarted, no payment was created, and no evidence was published.'
    return
}

$operator = (Read-Host 'Accountable operator name (do not enter email address)').Trim()
if ($operator.Length -lt 2 -or $operator -match '[=@]') { Stop-Safely 'A short operator name is required; do not enter an email address or secret.' }
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$runDirectory = Join-Path $EvidenceRoot "run-$stamp"
$scenarioDirectory = Join-Path $runDirectory 'scenarios'
New-Item -ItemType Directory -Path $scenarioDirectory -Force | Out-Null
@(
    'Staging payment-chaos orchestration preflight'
    "Recorded UTC: $([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))"
    "Staging URL: $ExpectedStagingUrl"
    "Application release SHA: $deployedSha"
    "Enterprise Release Qualification URL: $($qualification.RunUrl)"
    "Qualification artifact: $($qualification.ArtifactName)"
    'Public HTTPS/HSTS and staging runtime guards: PASS'
    'Preflight alone does not prove any payment-chaos scenario.'
) | Set-Content -LiteralPath (Join-Path $runDirectory 'preflight.txt') -Encoding UTF8

Write-Host ''
Write-Host '=== Guided staging payment-chaos scenarios ===' -ForegroundColor Cyan
Write-Host 'This runner does not fabricate PASS results. Complete each real sandbox scenario and record the observed outcome.'
Write-Host 'Do not enter customer PII, passwords, payment details, API keys, bearer tokens, or MFA seeds.'
try { Start-Process $ExpectedStagingUrl | Out-Null } catch { Write-Host 'Open the staging site manually in your browser.' }

$answerFile = Join-Path $runDirectory 'answers.txt'
$answerLines = New-Object System.Collections.Generic.List[string]
$scenarioResults = @{}
foreach ($scenario in (Get-ScenarioPlan)) {
    Write-Host ''
    Write-Host "$($scenario.Id) - $($scenario.Title)" -ForegroundColor Yellow
    Write-Host $scenario.Instruction
    $restartRecord = ''
    $restartRequired = $scenario.Id -in @('c8', 'c9')
    $restartSucceeded = $false

    if ($scenario.Id -eq 'c8') {
        $confirm = Read-Host "Immediately after Cashfree sandbox success while processing is in flight, type exactly 'RESTART STAGING POSTGRES'; otherwise type SKIP"
        if ($confirm -ceq 'RESTART STAGING POSTGRES') {
            try {
                $restartRecord = (@(Invoke-StagingRestart -Service postgres -ExpectedSha $deployedSha) -join '; ')
                $restartSucceeded = $true
                Write-Host 'Staging PostgreSQL restarted and readiness recovered.' -ForegroundColor Green
            } catch {
                $restartRecord = 'Restart/readiness check failed; investigate staging before continuing.'
                Write-Host $restartRecord -ForegroundColor Red
            }
        } else { $restartRecord = 'Staging PostgreSQL restart skipped; scenario cannot pass.' }
    } elseif ($scenario.Id -eq 'c9') {
        $confirm = Read-Host "After starting checkout/recovery traffic in another window, type exactly 'RESTART STAGING REDIS'; otherwise type SKIP"
        if ($confirm -ceq 'RESTART STAGING REDIS') {
            try {
                $restartRecord = (@(Invoke-StagingRestart -Service redis -ExpectedSha $deployedSha) -join '; ')
                $restartSucceeded = $true
                Write-Host 'Staging Redis restarted and readiness recovered.' -ForegroundColor Green
            } catch {
                $restartRecord = 'Restart/readiness check failed; investigate staging before continuing.'
                Write-Host $restartRecord -ForegroundColor Red
            }
        } else { $restartRecord = 'Staging Redis restart skipped; scenario cannot pass.' }
    }

    $outcome = (Read-Host 'Observed result: P = expected behavior verified, F = failed, S = skipped').Trim().ToUpperInvariant()
    if ($outcome -notin @('P', 'F', 'S')) { $outcome = 'S' }
    if ($restartRequired -and -not $restartSucceeded) { $outcome = 'S' }
    $observation = Read-Host 'Describe the actual observed result (no customer data or secrets)'
    if (-not $observation -or $observation.Trim().Length -lt 8) {
        $observation = 'Insufficient observed-result detail was supplied; this scenario is not accepted as a pass.'
        if ($outcome -eq 'P') { $outcome = 'F' }
    } elseif (-not (Test-SafeEvidenceText -Value $observation)) {
        $observation = 'Observation rejected because it appears to contain personal data or secret material; provide a sanitized, outcome-only note.'
        if ($outcome -eq 'P') { $outcome = 'F' }
        Write-Warning 'Sensitive-looking observation was not written to evidence. Provide an outcome-only note.'
    }
    $orderReference = Read-Host 'Disposable LK- order reference only (Enter to omit)'
    if (-not $orderReference -or $orderReference -notmatch '^LK-[A-Za-z0-9._-]{1,80}$') { $orderReference = 'OMITTED' }

    if ($restartRecord) { $observation = "$observation Restart record: $restartRecord" }
    $label = switch ($outcome) { 'P' { 'PASS_REPORTED_BY_OPERATOR' } 'F' { 'FAIL' } default { 'NOT_RUN_OR_SKIPPED' } }
    Write-ScenarioEvidence -RunDirectory $scenarioDirectory -Scenario $scenario -Operator $operator -ReleaseSha $deployedSha -QualificationUrl $qualification.RunUrl -Outcome $label -Observation $observation.Trim() -OrderReference $orderReference -RestartLog $restartRecord
    $scenarioResults[$scenario.Id] = $outcome
    if ($outcome -eq 'P') { $answerLines.Add("$($scenario.Id)=yes:operator observed expected behavior and recorded the result") }
    elseif ($outcome -eq 'F') { $answerLines.Add("$($scenario.Id)=no:scenario failed or did not meet expected postconditions") }
    else { $answerLines.Add("$($scenario.Id)=no:scenario skipped or not completely executed") }
}

$allNinePassed = $true
foreach ($id in @('c1','c2','c3','c4','c5','c6','c7','c8','c9')) {
    if ($scenarioResults[$id] -ne 'P') { $allNinePassed = $false; break }
}
$confirmSandbox = Read-Host 'Confirm all scenarios used only staging and Cashfree sandbox/test payments. Type exactly YES'
if ($allNinePassed -and $confirmSandbox -ceq 'YES') {
    $answerLines.Add('c10=yes:all scenarios completed on staging with sandbox/test payment credentials only')
} else {
    $answerLines.Add('c10=no:one or more scenarios failed/skipped or sandbox-only execution was not confirmed')
}
# The answer file is parsed by Bash grep; use ASCII to avoid the UTF-8 BOM emitted by Windows PowerShell 5.1.
$answerLines | Set-Content -LiteralPath $answerFile -Encoding Ascii

if ($RecoveryWaitMinutes -gt 0) {
    Write-Host "Waiting $RecoveryWaitMinutes minute(s) for staging recovery jobs before read-only invariant checks."
    for ($remaining = $RecoveryWaitMinutes; $remaining -gt 0; $remaining--) {
        Write-Host "$remaining minute(s) remaining..."
        Start-Sleep -Seconds 60
    }
} else {
    Write-Warning 'Recovery wait skipped by explicit -RecoveryWaitMinutes 0.'
}

$bash = Get-GitBash
$scriptBash = ConvertTo-BashPath (Join-Path $repoRoot 'infra\readiness\payment-chaos.sh')
$evidenceBash = ConvertTo-BashPath $runDirectory
$scenarioBash = ConvertTo-BashPath $scenarioDirectory
$answersBash = ConvertTo-BashPath $answerFile
$environmentNames = @('CONFIRM_STAGING_SANDBOX','PSQL_CMD','RELEASE_SHA','E2E_RUN_SHA','E2E_RUN_URL','OPERATOR','EVIDENCE_DIR','SCENARIO_EVIDENCE_DIR','ANSWERS_FILE','EVIDENCE_PREFIX')
$oldEnvironment = @{}
foreach ($name in $environmentNames) {
    $current = Get-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    $oldEnvironment[$name] = if ($current) { [string]$current.Value } else { $null }
}
$paymentChaosExit = 1
try {
    Set-Item Env:CONFIRM_STAGING_SANDBOX -Value 'yes'
    Set-Item Env:PSQL_CMD -Value "ssh -o BatchMode=yes -o ConnectTimeout=15 $SshAlias docker exec -i lakhdatar-staging-postgres psql -U lakhdatar -d lakhdatar_staging"
    Set-Item Env:RELEASE_SHA -Value $deployedSha
    Set-Item Env:E2E_RUN_SHA -Value $deployedSha
    Set-Item Env:E2E_RUN_URL -Value $qualification.RunUrl
    Set-Item Env:OPERATOR -Value $operator
    Set-Item Env:EVIDENCE_DIR -Value $evidenceBash
    Set-Item Env:SCENARIO_EVIDENCE_DIR -Value $scenarioBash
    Set-Item Env:ANSWERS_FILE -Value $answersBash
    Set-Item Env:EVIDENCE_PREFIX -Value 'production/readiness-evidence'
    Push-Location $repoRoot
    try {
        Write-Host '=== Read-only staging financial invariants and evidence report ===' -ForegroundColor Cyan
        & $bash.Source $scriptBash
        $paymentChaosExit = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    foreach ($name in $environmentNames) {
        if ($null -eq $oldEnvironment[$name]) { Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item -Path "Env:$name" -Value $oldEnvironment[$name] }
    }
}

if ($paymentChaosExit -ne 0) {
    Write-Host "Payment-chaos qualification did not pass. Local evidence retained under: $runDirectory" -ForegroundColor Yellow
    Write-Host 'Any upload below publishes a FAIL/blocked record; it does not turn the run into a pass.'
    $failedVariablesFile = Join-Path $runDirectory 'variables.env'
    if (Test-Path -LiteralPath $failedVariablesFile) {
        $failedEvidenceLine = Get-Content -LiteralPath $failedVariablesFile | Where-Object { $_ -match '^PAYMENT_CHAOS_EVIDENCE_ID=' } | Select-Object -Last 1
        $failedResultLine = Get-Content -LiteralPath $failedVariablesFile | Where-Object { $_ -match '^PAYMENT_CHAOS_RESULT_ID=' } | Select-Object -Last 1
        if ($failedEvidenceLine -and $failedResultLine) {
            $failedEvidenceId = ($failedEvidenceLine -replace '^PAYMENT_CHAOS_EVIDENCE_ID=', '').Trim()
            $failedResultId = ($failedResultLine -replace '^PAYMENT_CHAOS_RESULT_ID=', '').Trim()
            $failedArtifactPath = Join-Path $runDirectory ([System.IO.Path]::GetFileName($failedEvidenceId))
            $failedResultPath = Join-Path $runDirectory ([System.IO.Path]::GetFileName($failedResultId))
            if ((Test-Path -LiteralPath $failedArtifactPath) -and (Test-Path -LiteralPath $failedResultPath)) {
                Write-Host ('Latest payment-chaos result is FAIL: {0}' -f $failedResultId) -ForegroundColor Yellow
                $failedUploadPrompt = 'To upload the failed evidence bundle and FAIL result JSON to R2 and verify read-back, type exactly ''UPLOAD FAILED PAYMENT CHAOS EVIDENCE''; otherwise keep it local'
                $failedUploadConfirm = Read-Host $failedUploadPrompt
                if ($failedUploadConfirm -ceq 'UPLOAD FAILED PAYMENT CHAOS EVIDENCE') {
                    Upload-VerifiedEvidenceToR2 -ArtifactPath $failedArtifactPath -EvidenceId $failedEvidenceId
                    Upload-VerifiedEvidenceToR2 -ArtifactPath $failedResultPath -EvidenceId $failedResultId
                    Write-Host 'FAIL evidence and result JSON read-back verified. This published record keeps readiness blocked.'
                } else {
                    Write-Host 'Failed evidence upload skipped. It remains local; no GitHub Environment variable change was made.'
                }
            } else {
                Write-Host 'No complete evidence bundle/result pair is available to upload. Readiness remains blocked.'
            }
        } else {
            Write-Host 'No completed PAYMENT_CHAOS evidence/result IDs exist. This appears to be a preflight/collection failure; no R2 upload was attempted.'
        }
    } else {
        Write-Host 'No local variables.env/result record exists; no R2 upload was attempted.'
    }
    exit 1
}

$variablesFile = Join-Path $runDirectory 'variables.env'
$evidenceLine = Get-Content -LiteralPath $variablesFile | Where-Object { $_ -match '^PAYMENT_CHAOS_EVIDENCE_ID=' } | Select-Object -Last 1
$resultLine = Get-Content -LiteralPath $variablesFile | Where-Object { $_ -match '^PAYMENT_CHAOS_RESULT_ID=' } | Select-Object -Last 1
if (-not $evidenceLine -or -not $resultLine) { Stop-Safely 'The script did not produce both the payment-chaos evidence ID and result JSON ID; refusing upload.' }
$evidenceId = ($evidenceLine -replace '^PAYMENT_CHAOS_EVIDENCE_ID=', '').Trim()
$resultId = ($resultLine -replace '^PAYMENT_CHAOS_RESULT_ID=', '').Trim()
$artifactPath = Join-Path $runDirectory ([System.IO.Path]::GetFileName($evidenceId))
$resultPath = Join-Path $runDirectory ([System.IO.Path]::GetFileName($resultId))
if (-not (Test-Path -LiteralPath $resultPath)) { Stop-Safely 'The machine-readable result JSON is missing; refusing upload.' }
Write-Host ('Candidate payment-chaos evidence ID: {0}' -f $evidenceId) -ForegroundColor Green
Write-Host ('Candidate payment-chaos result ID: {0}' -f $resultId) -ForegroundColor Green
Write-Host ('Local evidence directory: {0}' -f $runDirectory)
Write-Host 'GitHub Environment variables have NOT been changed.'
$uploadPrompt = 'To upload the evidence bundle and its result JSON to R2 and verify read-back, type exactly ''UPLOAD PAYMENT CHAOS EVIDENCE TO R2''; otherwise keep it local'
$uploadConfirm = Read-Host $uploadPrompt
if ($uploadConfirm -ceq 'UPLOAD PAYMENT CHAOS EVIDENCE TO R2') {
    Upload-VerifiedEvidenceToR2 -ArtifactPath $artifactPath -EvidenceId $evidenceId
    Upload-VerifiedEvidenceToR2 -ArtifactPath $resultPath -EvidenceId $resultId
    Write-Host 'R2 evidence bundle, result JSON, and SHA-256 sidecars were read-back verified. GitHub Environment variables remain unchanged.'
} else {
    Write-Host 'R2 upload skipped. Evidence and result JSON remain local; GitHub Environment variables remain unchanged.'
}
) { Stop-Safely 'Evidence bucket must be a simple lowercase R2 bucket name.' }
if ($EvidenceBucket -eq 'neelastack-prod-db-backups') { Stop-Safely 'Refusing to store readiness evidence in the production backup bucket; use the dedicated readiness evidence bucket.' }
$ApiRoot = 'https://api.github.com/repos/cyberhackstar/lakhdatar-events'
