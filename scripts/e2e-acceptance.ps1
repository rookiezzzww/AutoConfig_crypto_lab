$ErrorActionPreference = 'Stop'

$rootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$baseUrl = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:8080' }

function Invoke-Json {
    param([string]$Url, [string]$Method = 'GET', [hashtable]$Payload)
    if ($Payload) {
        return Invoke-RestMethod -Uri $Url -Method $Method -ContentType 'application/json; charset=utf-8' `
            -Body ($Payload | ConvertTo-Json -Depth 5) -TimeoutSec 1200
    }
    return Invoke-RestMethod -Uri $Url -Method $Method -TimeoutSec 30
}

function Assert-CurrentScenario {
    param([string]$ExpectedId)
    $resp = Invoke-Json -Url "$baseUrl/api/scenarios/current"
    if (-not $resp.success -or $resp.data.id -ne $ExpectedId) {
        throw "current scenario mismatch. expected=$ExpectedId actual=$($resp.data.id)"
    }
}

function Activate-Scenario {
    param([string]$Id, [hashtable]$Options)
    $payload = @{ options = $Options; stopPrevious = $true }
    $resp = Invoke-Json -Url "$baseUrl/api/scenarios/$Id/activate" -Method 'POST' -Payload $payload
    if (-not $resp.success) { throw "activate failed: $Id" }
}

Set-Location $rootDir
Write-Host '[1/8] reset and start control services only'
docker compose down | Out-Host
docker compose up -d --build | Out-Host

Write-Host '[2/8] wait for control-plane API'
$ready = $false
for ($i = 0; $i -lt 120; $i++) {
    try { $null = Invoke-Json -Url "$baseUrl/api/system/status"; $ready = $true; break }
    catch { Start-Sleep -Seconds 2 }
}
if (-not $ready) { throw 'control-plane API did not become ready in time' }

foreach ($name in @('crypto-control', 'crypto-haproxy')) {
    if (-not (docker ps -q -f "name=^/$name$")) { throw "service is not running: $name" }
}
foreach ($name in @('crypto-heartbleed', 'crypto-poodle', 'crypto-sweet32')) {
    if (docker ps -q -f "name=^/$name$") { throw "scenario was prestarted unexpectedly: $name" }
}
$initial = Invoke-Json -Url "$baseUrl/api/scenarios/current"
if ($null -ne $initial.data) { throw 'a scenario is active before initialization' }

$cpStartedBefore = docker inspect -f '{{.State.StartedAt}}' crypto-control
$haStartedBefore = docker inspect -f '{{.State.StartedAt}}' crypto-haproxy

Write-Host '[3/8] initialize heartbleed on demand'
Activate-Scenario -Id 'heartbleed' -Options @{ tlsProfile = 'tls1_2' }
Assert-CurrentScenario -ExpectedId 'heartbleed'

Write-Host '[4/8] switch to poodle and stop heartbleed'
Activate-Scenario -Id 'poodle' -Options @{ protocol = 'ssl3' }
Assert-CurrentScenario -ExpectedId 'poodle'

Write-Host '[5/8] switch to sweet32 and stop poodle'
Activate-Scenario -Id 'sweet32' -Options @{ cipherSuite = 'DES-CBC3-SHA' }
Assert-CurrentScenario -ExpectedId 'sweet32'

Write-Host '[6/8] verify previous containers are stopped'
if ((docker inspect -f '{{.State.Running}}' crypto-heartbleed) -ne 'false') { throw 'heartbleed should be stopped' }
if ((docker inspect -f '{{.State.Running}}' crypto-poodle) -ne 'false') { throw 'poodle should be stopped' }

Write-Host '[7/8] verify audit trail'
$items = @((Invoke-Json -Url "$baseUrl/api/audit/logs").data)
if (-not ($items | Where-Object { $_.previousScenario -eq 'heartbleed' -and $_.targetScenario -eq 'poodle' })) {
    throw 'audit log missing heartbleed -> poodle'
}

Write-Host '[8/8] verify control services did not restart'
if ($cpStartedBefore -ne (docker inspect -f '{{.State.StartedAt}}' crypto-control)) { throw 'control-plane restarted unexpectedly' }
if ($haStartedBefore -ne (docker inspect -f '{{.State.StartedAt}}' crypto-haproxy)) { throw 'haproxy restarted unexpectedly' }

Write-Host 'ALL ACCEPTANCE TESTS PASSED'
