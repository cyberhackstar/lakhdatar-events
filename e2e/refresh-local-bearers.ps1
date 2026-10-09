$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Dot-source this file from PowerShell so the refreshed variables remain in the current session:
#   . .\refresh-local-bearers.ps1

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

$backendBase = 'http://127.0.0.1:8081'
$edgeBase = 'http://127.0.0.1:4002'

function Invoke-JsonPost([string]$Uri, [object]$Body) {
    $json = $Body | ConvertTo-Json -Depth 8
    try {
        return Invoke-RestMethod -Method Post -Uri $Uri -ContentType 'application/json' -Body $json -ErrorAction Stop
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $detail = ''
        if ($_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $detail = $reader.ReadToEnd()
                $reader.Close()
            } catch { }
        }
        throw "POST $Uri failed with HTTP $status. $detail"
    }
}

$adminCred = Get-Credential -UserName 'e2e-admin@example.test' -Message 'Local E2E admin credentials'
$staffCred = Get-Credential -UserName 'e2e-staff@example.test' -Message 'Local E2E staff credentials'

$adminLogin = Invoke-JsonPost "$backendBase/api/v1/auth/login" @{
    email = $adminCred.UserName
    password = $adminCred.GetNetworkCredential().Password
}

if (-not $adminLogin.accessToken) {
    throw 'Admin login succeeded without an access token; check whether local MFA is enabled.'
}

$staffPassword = $staffCred.GetNetworkCredential().Password
$staffLogin = Invoke-JsonPost "$backendBase/api/v1/auth/login" @{
    email = $staffCred.UserName
    password = $staffPassword
}

if (-not $staffLogin.accessToken) {
    throw 'Staff login succeeded without an access token; check local MFA/account state.'
}

$env:E2E_ENV = 'local'
$env:E2E_BASE_URL = $edgeBase
$env:E2E_ADMIN_BEARER = $adminLogin.accessToken
$env:E2E_STAFF_BEARER = $staffLogin.accessToken
$env:E2E_STAFF_EMAIL = $staffCred.UserName
$env:E2E_STAFF_PASSWORD = $staffPassword

$admin = Invoke-WebRequest -Method Get -Uri "$edgeBase/api/v1/admin/dashboard" -Headers @{ Authorization = "Bearer $env:E2E_ADMIN_BEARER" } -UseBasicParsing
$staff = Invoke-WebRequest -Method Get -Uri "$edgeBase/api/v1/staff/events" -Headers @{ Authorization = "Bearer $env:E2E_STAFF_BEARER" } -UseBasicParsing

Write-Host "Admin authorization: HTTP $($admin.StatusCode)"
Write-Host "Staff authorization: HTTP $($staff.StatusCode)"
Write-Host "E2E_BASE_URL: $env:E2E_BASE_URL"
Write-Host 'Bearer tokens refreshed in the current PowerShell session.'
