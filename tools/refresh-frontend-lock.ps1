$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..' 'frontend')
Write-Host 'Refreshing frontend package-lock.json from package.json...' -ForegroundColor Cyan
npm install --package-lock-only --ignore-scripts --no-fund
if ($LASTEXITCODE -ne 0) { throw 'npm package-lock refresh failed.' }
Write-Host 'Verifying clean install...' -ForegroundColor Cyan
npm ci --ignore-scripts --no-fund
if ($LASTEXITCODE -ne 0) { throw 'npm ci verification failed.' }
Write-Host 'Running high-severity dependency audit...' -ForegroundColor Cyan
npm audit --audit-level=high
if ($LASTEXITCODE -ne 0) { throw 'High-severity dependency audit failed.' }
Write-Host 'Frontend dependency lock remediation completed.' -ForegroundColor Green
