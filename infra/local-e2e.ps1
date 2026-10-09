$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

Write-Host "==> Checking local environment"
docker version | Out-Host
docker compose version | Out-Host

Write-Host "==> Starting local application stack"
docker compose up -d postgres redis backend

docker compose ps -a | Out-Host

Write-Host "==> Starting SSR web"
docker compose up -d web
Start-Sleep -Seconds 3

docker compose ps -a | Out-Host

Write-Host "==> Starting edge"
docker compose up -d edge
Start-Sleep -Seconds 3

docker compose ps -a | Out-Host

Write-Host "==> Waiting for edge health"
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
  try {
    $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:4002/edge-health' -TimeoutSec 5
    if ($r.StatusCode -eq 200) { $healthy = $true; break }
  } catch { }
  Start-Sleep -Seconds 2
}
if (-not $healthy) {
  Write-Host "==> Edge did not become healthy"
  docker compose ps -a | Out-Host
  docker compose logs --tail=200 web | Out-Host
  docker compose logs --tail=200 edge | Out-Host
  throw 'Local edge health check failed.'
}

Write-Host "==> Local edge is healthy: http://127.0.0.1:4002"

Push-Location (Join-Path $root 'e2e')
try {
  npm ci
  npm run install:chromium
  $env:E2E_ENV = 'local'
  $env:E2E_BASE_URL = 'http://127.0.0.1:4002'
  Write-Host "==> Running public security browser gate"
  npm test -- tests/public-security.spec.js --project=chromium
} finally {
  Pop-Location
}
