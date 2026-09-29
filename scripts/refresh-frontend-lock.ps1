$ErrorActionPreference = "Stop"

Write-Host "Neelastack frontend dependency baseline refresh" -ForegroundColor Cyan

$node = node --version 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($node)) { throw "Node.js 22.x is required but Node.js could not be found. Activate the project Node version before continuing." }
$node = $node.Trim()
if (-not $node.StartsWith("v22.")) { throw "Node 22.x is required. Select the project baseline from .nvmrc before continuing. Current: $node" }

function Invoke-Npm {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    & npm @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "npm $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

Push-Location "$PSScriptRoot\..\frontend"
try {
    if (Test-Path node_modules) { Remove-Item -Recurse -Force node_modules }
    Invoke-Npm @("install", "--no-audit", "--no-fund")
    Invoke-Npm @("run", "verify:baseline")
    Invoke-Npm @("ci", "--no-audit", "--no-fund")
    Invoke-Npm @("run", "build")
} finally {
    Pop-Location
}

Write-Host "Frontend lockfile refresh and production build completed." -ForegroundColor Green
