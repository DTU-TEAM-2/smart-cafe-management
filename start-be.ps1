$ErrorActionPreference = 'Stop'
$taskOriginalEnvironment = @{
    DB_URL = $env:DB_URL
    DB_USERNAME = $env:DB_USERNAME
    DB_PASSWORD = $env:DB_PASSWORD
}
$taskConfigKeys = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SMTP_HOST', 'SMTP_PORT', 'SMTP_USERNAME', 'SMTP_PASSWORD', 'SMTP_FROM', 'SMTP_AUTH', 'SMTP_STARTTLS', 'SMTP_SSL', 'AUTH_TOKEN_SECRET', 'SPRING_PROFILES_ACTIVE')
foreach ($taskConfigKey in $taskConfigKeys) {
    $taskOriginalEnvironment[$taskConfigKey] = [Environment]::GetEnvironmentVariable($taskConfigKey, 'Process')
}

try {
    $taskEnvPath = Join-Path $PSScriptRoot 'backend\.env'
    if (Test-Path -LiteralPath $taskEnvPath) {
        foreach ($taskLine in Get-Content -LiteralPath $taskEnvPath -Encoding UTF8) {
            if ($taskLine -match '^\s*([A-Z_]+)\s*=(.*)$' -and $taskConfigKeys -contains $Matches[1]) {
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

    if ([string]::IsNullOrWhiteSpace($env:SPRING_PROFILES_ACTIVE)) {
        $env:SPRING_PROFILES_ACTIVE = 'dev'
    }

    if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
        $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/smart_cafe?connectionTimeZone=UTC'
    }

    Push-Location -LiteralPath (Join-Path $PSScriptRoot 'backend')
    try {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            throw "Backend stopped with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($taskConfigKey in $taskConfigKeys) {
        [Environment]::SetEnvironmentVariable($taskConfigKey, $taskOriginalEnvironment[$taskConfigKey], 'Process')
    }
}
