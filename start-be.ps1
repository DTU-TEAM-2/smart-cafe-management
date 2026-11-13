param([string]$EnvFile = 'backend/.env')

$ErrorActionPreference = 'Stop'
$taskKeys = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT', 'AUTH_TOKEN_SECRET', 'OTP_VALIDITY_SECONDS', 'AUTH_TOKEN_VALIDITY_SECONDS', 'PENDING_REGISTRATION_VALIDITY_SECONDS', 'SMTP_HOST', 'SMTP_PORT', 'SMTP_USERNAME', 'SMTP_PASSWORD', 'SMTP_FROM', 'SMTP_AUTH', 'SMTP_STARTTLS', 'SMTP_SSL', 'SPRING_PROFILES_ACTIVE', 'CLOUDINARY_CLOUD_NAME', 'CLOUDINARY_API_KEY', 'CLOUDINARY_API_SECRET', 'NEWS_FIREBASE_BUCKET', 'GOOGLE_APPLICATION_CREDENTIALS', 'NEWS_FIREBASE_EMULATOR_HOST')
$taskOriginal = @{}
foreach ($taskKey in $taskKeys) {
    $taskOriginal[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process')
}
try {
    $taskEnv = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $PSScriptRoot $EnvFile }
    $taskEnv = [System.IO.Path]::GetFullPath($taskEnv)
    if ($PSBoundParameters.ContainsKey('EnvFile') -and -not (Test-Path -LiteralPath $taskEnv -PathType Leaf)) {
        throw 'The selected environment file does not exist. Copy the matching example and configure it first.'
    }
    if (Test-Path -LiteralPath $taskEnv) {
        foreach ($taskLine in Get-Content -LiteralPath $taskEnv -Encoding UTF8) {
            if ($taskLine -notmatch '^\s*([A-Z_][A-Z0-9_]*)\s*=(.*)$') { continue }
            $taskKey = $Matches[1]
            $taskValue = $Matches[2].Trim()
            if ($taskKeys -notcontains $taskKey) { continue }
            if ($taskValue.StartsWith('"') -or $taskValue.StartsWith("'")) {
                if ($taskValue.Length -lt 2 -or $taskValue[-1] -ne $taskValue[0]) {
                    throw "Unclosed quote for $taskKey in backend/.env."
                }
                $taskValue = $taskValue.Substring(1, $taskValue.Length - 2)
            }
            if ($null -eq $taskOriginal[$taskKey]) {
                if ($taskKey -eq 'GOOGLE_APPLICATION_CREDENTIALS' -and $taskValue -and -not [System.IO.Path]::IsPathRooted($taskValue)) {
                    $taskValue = [System.IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $taskEnv) $taskValue))
                }
                [Environment]::SetEnvironmentVariable($taskKey, $taskValue, 'Process')
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
        $env:DB_USERNAME = 'cafe_app'
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
