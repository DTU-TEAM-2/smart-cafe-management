param([switch]$WindowsNioWorkaround)

$ErrorActionPreference = 'Stop'
$taskKeys = @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD')
$taskOriginalEnvironment = @{}
foreach ($taskKey in $taskKeys) {
    $taskOriginalEnvironment[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process')
}

try {
    $taskEnvPath = Join-Path $PSScriptRoot 'backend\.env'
    if (Test-Path -LiteralPath $taskEnvPath) {
        foreach ($taskLine in Get-Content -LiteralPath $taskEnvPath -Encoding UTF8) {
            if ($taskLine -match '^\s*(TEST_DB_URL|TEST_DB_USERNAME|TEST_DB_PASSWORD)\s*=(.*)$') {
                $taskKey = $Matches[1]
                $taskValue = $Matches[2].Trim()
                if ($taskValue.StartsWith('"') -or $taskValue.StartsWith("'")) {
                    if ($taskValue.Length -ge 2 -and $taskValue[-1] -eq $taskValue[0]) {
                        $taskValue = $taskValue.Substring(1, $taskValue.Length - 2)
                    }
                }
                if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($taskKey, 'Process'))) {
                    [Environment]::SetEnvironmentVariable($taskKey, $taskValue, 'Process')
                }
            }
        }
    }

    if ($env:TEST_DB_URL -notmatch '^jdbc:mysql://[^/]+/[^?;/]+_test(?:\?|$)') {
        throw 'Set TEST_DB_URL to a dedicated MySQL schema ending in _test before running tests.'
    }
    if ([string]::IsNullOrWhiteSpace($env:TEST_DB_USERNAME) -or [string]::IsNullOrEmpty($env:TEST_DB_PASSWORD)) {
        throw 'Set TEST_DB_USERNAME and TEST_DB_PASSWORD in the environment or backend/.env.'
    }

    Push-Location -LiteralPath (Join-Path $PSScriptRoot 'backend')
    try {
        $taskMavenArguments = @('-B', '-ntp', 'verify')
        if ($WindowsNioWorkaround) { $taskMavenArguments += '-Dwindows.nio.workaround=true' }
        & .\mvnw.cmd @taskMavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Backend verification failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($taskKey in $taskKeys) {
        [Environment]::SetEnvironmentVariable($taskKey, $taskOriginalEnvironment[$taskKey], 'Process')
    }
}
