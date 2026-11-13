param([Parameter(Mandatory = $true)][string]$JdbcDriver)

$ErrorActionPreference = 'Stop'
$taskKeys = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')
$taskOriginal = @{}
foreach ($taskKey in $taskKeys) { $taskOriginal[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process') }
try {
    $taskDriver = (Resolve-Path -LiteralPath $JdbcDriver).Path
    $taskEnv = Join-Path $PSScriptRoot 'backend\.env'
    if (Test-Path -LiteralPath $taskEnv) {
        foreach ($taskLine in Get-Content -LiteralPath $taskEnv -Encoding UTF8) {
            if ($taskLine -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)\s*=(.*)$') {
                $taskKey = $Matches[1]
                $taskValue = $Matches[2].Trim()
                if ($taskValue.Length -ge 2 -and $taskValue[0] -in @('"', "'") -and $taskValue[-1] -eq $taskValue[0]) {
                    $taskValue = $taskValue.Substring(1, $taskValue.Length - 2)
                }
                if ($null -eq [Environment]::GetEnvironmentVariable($taskKey, 'Process')) {
                    [Environment]::SetEnvironmentVariable($taskKey, $taskValue, 'Process')
                }
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_URL) -or [string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
        throw 'Supply explicit DB_URL/DB_USERNAME privately; this read-only preflight has no database fallback.'
    }
    $taskResources = Join-Path $PSScriptRoot 'backend\src\main\resources'
    $taskSource = Join-Path $PSScriptRoot 'backend\src\main\java\vn\codegym\smartcafe\database\DatabasePreflight.java'
    # Source-file launch uses the existing MySQL driver; Spring/Flyway are never started.
    & java --class-path "$taskDriver$([IO.Path]::PathSeparator)$taskResources" $taskSource
    $taskExit = $LASTEXITCODE
} finally {
    foreach ($taskKey in $taskKeys) { [Environment]::SetEnvironmentVariable($taskKey, $taskOriginal[$taskKey], 'Process') }
}
exit $taskExit
