# Loads .env into the current process environment, then runs the app.
#
# Spring Boot doesn't auto-load .env files the way python-dotenv (ai-service)
# or Vite (both frontends) do, so application.properties' ${JWT_SECRET},
# ${RAPIDAPI_KEY}, ${AWS_S3_*} placeholders resolve to nothing when running
# `mvnw spring-boot:run` directly -- this script closes that gap for local dev.
# docker-compose doesn't need this; it reads the root .env on its own.

$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Warning ".env not found at $envFile -- copy .env.example to .env and fill in real values first."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $key, $value = $line -split "=", 2
        [System.Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim())
    }
}

& "$PSScriptRoot\mvnw.cmd" spring-boot:run
