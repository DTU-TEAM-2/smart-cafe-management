$ErrorActionPreference = 'Stop'
$taskKeys = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT', 'AUTH_TOKEN_SECRET', 'SMTP_HOST', 'SMTP_PORT', 'SMTP_USERNAME', 'SMTP_PASSWORD', 'SMTP_FROM', 'SMTP_AUTH', 'SMTP_STARTTLS', 'SMTP_SSL', 'SPRING_PROFILES_ACTIVE', 'CLOUDINARY_CLOUD_NAME', 'CLOUDINARY_API_KEY', 'CLOUDINARY_API_SECRET')
$taskOriginal = @{}
foreach ($taskKey in $taskKeys) {
    $taskOriginal[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process')
}
try {
    $taskEnv = Join-Path $PSScriptRoot 'backend\.env'
    if (Test-Path -LiteralPath $taskEnv) {
        foreach ($taskLine in Get-Content -LiteralPath $taskEnv -Encoding UTF8) {
            if ($taskLine -match '^\s*([A-Za-z0-9_]+)\s*=(.*)$' -and $taskKeys -contains $Matches[1]) {
                $taskKey = $Matches[1]
                $taskValue = $Matches[2].Trim()
                if ($taskValue.StartsWith('"') -or $taskValue.StartsWith("'")) {
                    if ($taskValue.Length -ge 2 -and $taskValue[-1] -eq $taskValue[0]) {
                        $taskValue = $taskValue.Substring(1, $taskValue.Length - 2)
                    }
                }
                if ($null -eq $taskOriginal[$taskKey]) {
                    [Environment]::SetEnvironmentVariable($taskKey, $taskValue, 'Process')
                }
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
        $env:DB_USERNAME = 'root'
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
        $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/smart_cafe?connectionTimeZone=UTC'
    }
    Push-Location -LiteralPath (Join-Path $PSScriptRoot 'backend')
    try {
        & .\mvnw.cmd -B -ntp spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    foreach ($taskKey in $taskKeys) {
        [Environment]::SetEnvironmentVariable($taskKey, $taskOriginal[$taskKey], 'Process')
    }
}
