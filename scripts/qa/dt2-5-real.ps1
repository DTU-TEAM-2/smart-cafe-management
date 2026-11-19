param([Parameter(Mandatory = $true)][string]$Container)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskDatabase = 'smart_cafe_dt25_http_' + [Guid]::NewGuid().ToString('N').Substring(0, 10) + '_test'
if ($taskDatabase -notmatch '^smart_cafe_dt25_http_[a-f0-9]{10}_test$') { throw 'Invalid isolated schema name.' }
$taskJar = Join-Path $taskRoot 'backend/target/smart-cafe-0.0.1-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $taskJar)) { throw 'Run backend Maven verify before real HTTP acceptance.' }
$taskNames = @('DB_URL','DB_USERNAME','DB_PASSWORD','SERVER_PORT','AUTH_TOKEN_SECRET','FEEDBACK_TEST_SECRET','TEST_DB_URL','FEEDBACK_TEST_BASE','FEEDBACK_TEST_API','VITE_API_PROXY_TARGET','PLAYWRIGHT_BROWSERS_PATH','SPRING_FLYWAY_ENABLED','FEEDBACK_FIREBASE_ENABLED')
$taskPrevious = @{}
foreach ($taskName in $taskNames) { $taskPrevious[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process') }
$taskBackend = $null
$taskFrontend = $null
try {
    & docker exec $Container mysql -uroot -e "CREATE DATABASE $taskDatabase"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create fresh isolated schema.' }
    Get-Content -LiteralPath (Join-Path $taskRoot 'backend/src/test/resources/schema.sql') -Raw | & docker exec -i $Container mysql --default-character-set=utf8mb4 -uroot $taskDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Test schema provision failed.' }
    Get-Content -LiteralPath (Join-Path $taskRoot 'docs/qa/dt2-5-http-fixture.sql') -Raw | & docker exec -i $Container mysql --default-character-set=utf8mb4 -uroot $taskDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic fixture failed.' }
    $env:DB_URL = "jdbc:mysql://127.0.0.1:13326/$taskDatabase`?connectionTimeZone=UTC"
    $env:TEST_DB_URL = $env:DB_URL
    $env:DB_USERNAME = 'root'
    $env:DB_PASSWORD = ''
    $env:SERVER_PORT = '18085'
    $env:SPRING_FLYWAY_ENABLED = 'false'
    $env:FEEDBACK_FIREBASE_ENABLED = 'false'
    $env:AUTH_TOKEN_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
    $env:FEEDBACK_TEST_SECRET = $env:AUTH_TOKEN_SECRET
    $env:FEEDBACK_TEST_BASE = 'http://127.0.0.1:15175'
    $env:FEEDBACK_TEST_API = 'http://127.0.0.1:18085'
    $env:VITE_API_PROXY_TARGET = $env:FEEDBACK_TEST_API
    $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $env:LOCALAPPDATA 'ms-playwright'
    $taskBackend = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList @('-jar', $taskJar) -WindowStyle Hidden -PassThru -WorkingDirectory $taskRoot -RedirectStandardOutput (Join-Path $taskRoot 'backend/target/dt25-http.log') -RedirectStandardError (Join-Path $taskRoot 'backend/target/dt25-http-error.log')
    $taskFrontend = Start-Process -FilePath (Get-Command node).Source -ArgumentList @((Join-Path $taskRoot 'frontend/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '15175', '--strictPort') -WindowStyle Hidden -PassThru -WorkingDirectory (Join-Path $taskRoot 'frontend') -RedirectStandardOutput (Join-Path $taskRoot 'backend/target/dt25-vite.log') -RedirectStandardError (Join-Path $taskRoot 'backend/target/dt25-vite-error.log')
    foreach ($taskUrl in @('http://127.0.0.1:18085/actuator/health','http://127.0.0.1:15175')) {
        $taskReady = $false
        for ($taskAttempt = 0; $taskAttempt -lt 90; $taskAttempt++) {
            try { $taskResponse = Invoke-WebRequest -Uri $taskUrl -TimeoutSec 2 -UseBasicParsing; if ($taskResponse.StatusCode -eq 200) { $taskReady = $true; break } } catch { }
            Start-Sleep -Milliseconds 500
        }
        if (!$taskReady) { throw "Test service not ready: $taskUrl" }
    }
    Push-Location (Join-Path $taskRoot 'frontend')
    try {
        & node integration/dt2-5-real.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Real browser/Newman acceptance failed.' }
    } finally { Pop-Location }
    Write-Output "DT2-5 real HTTP acceptance completed on isolated schema $taskDatabase. No Firebase exercised."
} finally {
    foreach ($taskProcess in @($taskFrontend, $taskBackend)) {
        if ($null -ne $taskProcess -and !$taskProcess.HasExited) { Stop-Process -Id $taskProcess.Id }
    }
    foreach ($taskName in $taskNames) { [Environment]::SetEnvironmentVariable($taskName, $taskPrevious[$taskName], 'Process') }
}
