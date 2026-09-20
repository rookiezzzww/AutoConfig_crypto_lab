$ErrorActionPreference = 'Stop'

$rootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$baseUrl = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:8080' }

function Invoke-Json {
    param([string]$Url, [string]$Method = 'GET')
    return Invoke-RestMethod -Uri $Url -Method $Method -TimeoutSec 10
}

function Assert-CurrentScenario {
    param([string]$ExpectedId)
    $resp = Invoke-Json -Url "$baseUrl/api/scenarios/current"
    if (-not $resp.success) { throw "current scenario API failed" }
    if ($resp.data.id -ne $ExpectedId) {
        throw "current scenario mismatch. expected=$ExpectedId actual=$($resp.data.id)"
    }
}

function Activate-Scenario {
    param([string]$Id)
    $resp = Invoke-Json -Url "$baseUrl/api/scenarios/$Id/activate" -Method 'POST'
    if (-not $resp.success) { throw "activate failed: $Id" }
}

Set-Location $rootDir
Write-Host '[1/8] starting services'
docker compose up -d --build | Out-Host

Write-Host '[2/8] waiting control-plane API'
$ready = $false
for ($i = 0; $i -lt 120; $i++) {
    try {
        $null = Invoke-Json -Url "$baseUrl/api/system/status"
        $ready = $true
        break
    }
    catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) { throw 'control-plane API did not become ready in time' }

$required = @('crypto-control','crypto-haproxy','crypto-heartbleed','crypto-poodle','crypto-sweet32')
foreach ($name in $required) {
    $id = docker ps -q -f "name=^/$name$"
    if ([string]::IsNullOrWhiteSpace($id)) { throw "service is not running: $name" }
}

$cpStartedBefore = (docker inspect -f '{{.State.StartedAt}}' crypto-control)
$haStartedBefore = (docker inspect -f '{{.State.StartedAt}}' crypto-haproxy)

Write-Host '[3/8] default active scenario'
Assert-CurrentScenario -ExpectedId 'heartbleed'

Write-Host '[4/8] switch heartbleed -> poodle'
Activate-Scenario -Id 'poodle'
Assert-CurrentScenario -ExpectedId 'poodle'

Write-Host '[5/8] switch poodle -> sweet32'
Activate-Scenario -Id 'sweet32'
Assert-CurrentScenario -ExpectedId 'sweet32'

Write-Host '[6/8] switch sweet32 -> heartbleed'
Activate-Scenario -Id 'heartbleed'
Assert-CurrentScenario -ExpectedId 'heartbleed'

Write-Host '[7/8] verify audit trail'
$audit = Invoke-Json -Url "$baseUrl/api/audit/logs"
if (-not $audit.success) { throw 'audit API failed' }
$items = @($audit.data)
$hasH2P = $items | Where-Object { $_.previousScenario -eq 'heartbleed' -and $_.targetScenario -eq 'poodle' }
$hasP2S = $items | Where-Object { $_.previousScenario -eq 'poodle' -and $_.targetScenario -eq 'sweet32' }
if (-not $hasH2P) { throw 'audit log missing heartbleed -> poodle' }
if (-not $hasP2S) { throw 'audit log missing poodle -> sweet32' }

Write-Host '[8/8] verify no restart'
$cpStartedAfter = (docker inspect -f '{{.State.StartedAt}}' crypto-control)
$haStartedAfter = (docker inspect -f '{{.State.StartedAt}}' crypto-haproxy)
if ($cpStartedBefore -ne $cpStartedAfter) { throw 'control-plane restarted unexpectedly' }
if ($haStartedBefore -ne $haStartedAfter) { throw 'haproxy restarted unexpectedly' }

Write-Host 'ALL ACCEPTANCE TESTS PASSED'
