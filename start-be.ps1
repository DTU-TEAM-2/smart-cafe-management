$ErrorActionPreference = 'Stop'
$taskKeys = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT', 'AUTH_TOKEN_SECRET', 'CLOUDINARY_CLOUD_NAME', 'CLOUDINARY_API_KEY', 'CLOUDINARY_API_SECRET')
$taskOriginal = @{}
foreach ($taskKey in $taskKeys) {
    $taskOriginal[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process')
}
try {
    $taskEnv = Join-Path $PSScriptRoot 'backend\.env'
    if (Test-Path -LiteralPath $taskEnv) {
        foreach ($taskLine in Get-Content -LiteralPath $taskEnv -Encoding UTF8) {
            if ($taskLine -notmatch '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD|SERVER_PORT|AUTH_TOKEN_SECRET|CLOUDINARY_CLOUD_NAME|CLOUDINARY_API_KEY|CLOUDINARY_API_SECRET)\s*=(.*)$') { continue }
            $taskKey = $Matches[1]
            $taskValue = $Matches[2].Trim()
            if ($taskValue.StartsWith('"') -or $taskValue.StartsWith("'")) {
                if ($taskValue.Length -lt 2 -or $taskValue[-1] -ne $taskValue[0]) {
                    throw "Unclosed quote for $taskKey in backend/.env."
                }
                $taskValue = $taskValue.Substring(1, $taskValue.Length - 2)
            }
            if ($null -eq $taskOriginal[$taskKey]) {
                [Environment]::SetEnvironmentVariable($taskKey, $taskValue, 'Process')
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
        throw 'Configure DB_USERNAME and DB_PASSWORD in backend/.env before starting.'
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
