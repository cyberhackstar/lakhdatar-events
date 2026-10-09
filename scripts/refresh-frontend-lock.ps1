$ErrorActionPreference = 'Stop'
Write-Host 'Neelastack frontend dependency baseline refresh' -ForegroundColor Cyan

$node = node --version 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($node)) { throw 'Node.js 22.19+ or 24.x is required.' }
$node = $node.Trim()
if (-not ($node -match '^v22\.(19|[2-9][0-9])' -or $node -match '^v24\.')) { throw "Unsupported Node.js runtime: $node" }

function Invoke-Npm {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    & npm @Arguments
    if ($LASTEXITCODE -ne 0) { throw "npm $($Arguments -join ' ') failed with exit code $LASTEXITCODE." }
}

Push-Location "$PSScriptRoot\..\frontend"
try {
    Invoke-Npm @('install','--package-lock-only','--include=dev','--ignore-scripts','--no-audit','--no-fund','--prefer-online')
    Invoke-Npm @('ci','--include=dev','--ignore-scripts','--no-audit','--no-fund')
    Invoke-Npm @('run','verify:dependencies')
    Invoke-Npm @('run','audit:high')
    Invoke-Npm @('run','audit:runtime')
    Invoke-Npm @('run','build')
} finally {
    Pop-Location
}
Write-Host 'Frontend dependency lock refresh and production build completed.' -ForegroundColor Green
