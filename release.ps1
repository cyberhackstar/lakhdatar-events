[CmdletBinding()]
param(
    [string]$Sha,
    [string]$Repo = 'cyberhackstar/lakhdatar-events',
    [string]$StagingUrl = 'https://staging-events.neelastack.com'
)

$ErrorActionPreference = 'Stop'

function Invoke-GhText {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $output = & gh @Arguments
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        throw "GitHub CLI failed with exit code $code."
    }
    return (($output -join "`n").Trim())
}

function Get-WorkflowRuns {
    param([Parameter(Mandatory)][string]$Workflow)

    $args = @(
        'run', 'list', '-R', $Repo,
        '--workflow', $Workflow,
        '--event', 'workflow_dispatch',
        '--limit', '100',
        '--json', 'databaseId,headSha,headBranch,event,status,conclusion,url'
    )

    $output = & gh @args
    if ($LASTEXITCODE -ne 0) {
        throw "Could not list runs for $Workflow."
    }

    $json = ($output -join "`n").Trim()
    if ([string]::IsNullOrWhiteSpace($json)) {
        return @()
    }

    return @(ConvertFrom-Json -InputObject $json -ErrorAction Stop)
}

function Start-AndWait {
    param(
        [Parameter(Mandatory)][string]$Workflow,
        [Parameter(Mandatory)][string[]]$Fields,
        [Parameter(Mandatory)][string]$PinnedMainSha
    )

    $currentMain = Invoke-GhText -Arguments @(
        'api', "repos/$Repo/commits/main", '--jq', '.sha'
    )

    if ($currentMain -ne $PinnedMainSha) {
        throw "GitHub main moved during release preparation. Stop and restart qualification for the new SHA."
    }

    $beforeIds = @(
        Get-WorkflowRuns -Workflow $Workflow |
        ForEach-Object { [string]$_.databaseId }
    )

    $dispatchArgs = @(
        'workflow', 'run', $Workflow,
        '-R', $Repo, '--ref', 'main'
    )
    foreach ($field in $Fields) {
        $dispatchArgs += @('-f', $field)
    }

    $dispatchOutput = & gh @dispatchArgs 2>&1
    $dispatchCode = $LASTEXITCODE

    if ($dispatchCode -ne 0) {
        throw "Could not dispatch $Workflow. $($dispatchOutput -join ' ')"
    }

    $runId = $null

    for ($attempt = 0; $attempt -lt 45 -and -not $runId; $attempt++) {
        Start-Sleep -Seconds 4

        $runs = @(Get-WorkflowRuns -Workflow $Workflow)

        $matches = @(
            $runs | Where-Object {
                ([string]$_.databaseId -notin $beforeIds) -and
                ([string]$_.headSha -eq $PinnedMainSha) -and
                ([string]$_.headBranch -eq 'main') -and
                ([string]$_.event -eq 'workflow_dispatch')
            }
        )

        if ($matches.Count -gt 1) {
            throw "Multiple new $Workflow runs were detected. No run was selected; inspect GitHub Actions."
        }

        if ($matches.Count -eq 1) {
            $runId = [string]$matches[0].databaseId
        }
    }

    if (-not $runId) {
        throw "Could not identify the exact new $Workflow run. Inspect GitHub Actions before retrying."
    }

    $runUrl = "https://github.com/$Repo/actions/runs/$runId"
    Write-Host "`n$Workflow"
    Write-Host "Run: $runUrl"

    & gh run watch $runId -R $Repo --exit-status --interval 15 |
        Out-Host

    if ($LASTEXITCODE -ne 0) {
        throw "$Workflow failed. Inspect $runUrl before continuing."
    }

    Write-Host "$Workflow passed." -ForegroundColor Green
    return $runId
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'GitHub CLI is missing. Install it and run gh auth login.'
}

$null = Invoke-GhText -Arguments @('auth', 'status')

if ($StagingUrl -ne 'https://staging-events.neelastack.com') {
    throw 'Unexpected staging URL. Use the reviewed staging URL.'
}

$PinnedMainSha = Invoke-GhText -Arguments @(
    'api', "repos/$Repo/commits/main", '--jq', '.sha'
)

if ($PinnedMainSha -notmatch '^[0-9a-f]{40}$') {
    throw 'Could not resolve the exact main SHA.'
}

if (-not $Sha) {
    $Sha = $PinnedMainSha
}

if ($Sha -notmatch '^[0-9a-f]{40}$') {
    throw 'Release SHA must be exactly 40 lowercase hexadecimal characters.'
}

Write-Host "Release SHA: $Sha"
Write-Host "Workflow dispatch SHA: $PinnedMainSha"

$comparison = Invoke-GhText -Arguments @(
    'api', "repos/$Repo/compare/main...$Sha", '--jq', '.status'
)

if ($comparison -notin @('identical', 'behind')) {
    throw "Release SHA is not the current main commit or one of its ancestors."
}

if ($Sha -ne $PinnedMainSha) {
    Write-Host 'WARNING: qualifying an older main commit, not current main.' -ForegroundColor Yellow
}

$ciJson = Invoke-GhText -Arguments @(
    'api',
    "repos/$Repo/actions/workflows/ci.yml/runs?head_sha=$Sha&branch=main&status=completed&per_page=100"
)

$ci = ConvertFrom-Json -InputObject $ciJson -ErrorAction Stop

$successfulCi = @(
    $ci.workflow_runs | Where-Object {
        $_.conclusion -eq 'success' -and
        $_.head_branch -eq 'main' -and
        $_.head_sha -eq $Sha
    }
)

if ($successfulCi.Count -lt 1) {
    throw "No successful CI run was found for this exact SHA on main."
}

Write-Host 'Exact-SHA CI check passed.' -ForegroundColor Green

$stagingRunId = Start-AndWait `
    -Workflow 'staging.yml' `
    -Fields @(
        "release_sha=$Sha",
        'staging_host=staging-events.neelastack.com'
    ) `
    -PinnedMainSha $PinnedMainSha

$qualificationRunId = Start-AndWait `
    -Workflow 'enterprise-release-qualification.yml' `
    -Fields @(
        "release_sha=$Sha",
        "staging_url=$StagingUrl"
    ) `
    -PinnedMainSha $PinnedMainSha

$readinessRunId = Start-AndWait `
    -Workflow 'single-node-production-readiness.yml' `
    -Fields @(
        "release_sha=$Sha",
        "qualification_run_id=$qualificationRunId"
    ) `
    -PinnedMainSha $PinnedMainSha

Write-Host ''
Write-Host 'PRE-PRODUCTION PIPELINE PASSED' -ForegroundColor Green
Write-Host "Release SHA:       $Sha"
Write-Host "Staging:           https://github.com/$Repo/actions/runs/$stagingRunId"
Write-Host "Qualification:     https://github.com/$Repo/actions/runs/$qualificationRunId"
Write-Host "Readiness:         https://github.com/$Repo/actions/runs/$readinessRunId"
Write-Host ''
Write-Host 'Production was NOT started.'
Write-Host 'Production approval remains a separate manual step.'
