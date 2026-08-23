# run-dev.ps1
# One-command way to run DocQuery: sets Java 17, loads .env, ensures
# Docker containers are up, then starts the app.
# Usage: from the backend folder, run:  .\run-dev.ps1

$ErrorActionPreference = "Stop"

# ---------- Java 17 ----------
# EDIT THIS PATH if your JDK 17 folder name is different - check with:
#   dir "C:\Program Files\Eclipse Adoptium\"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# ---------- Load variables from the project's .env file ----------
$envFile = Join-Path $PSScriptRoot "..\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $name = $matches[1]
            $value = $matches[2]
            [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
    Write-Host "Loaded .env from $envFile" -ForegroundColor Green
} else {
    Write-Host "WARNING: no .env file found at $envFile - using defaults / already-set variables." -ForegroundColor Yellow
}

# ---------- Safe defaults (only apply if not already set by .env) ----------
if (-not $env:DB_HOST)     { $env:DB_HOST = "localhost" }
if (-not $env:DB_PORT)     { $env:DB_PORT = "3307" }
if (-not $env:DB_USER)     { $env:DB_USER = "root" }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "root" }
if (-not $env:REDIS_HOST)  { $env:REDIS_HOST = "localhost" }
if (-not $env:REDIS_PORT)  { $env:REDIS_PORT = "6379" }
if (-not $env:JWT_SECRET)  { $env:JWT_SECRET = "please-change-this-to-a-long-random-secret-value-32chars" }

Write-Host ""
Write-Host "---- Config for this run ----" -ForegroundColor Cyan
Write-Host "JAVA_HOME : $env:JAVA_HOME"
Write-Host "DB_HOST   : $env:DB_HOST"
Write-Host "DB_PORT   : $env:DB_PORT"
Write-Host "REDIS_HOST: $env:REDIS_HOST"

if ($env:OPENAI_API_KEY) {
    $keyLength = $env:OPENAI_API_KEY.Length
    Write-Host "OPENAI_API_KEY is set (length: $keyLength)" -ForegroundColor Green
} else {
    Write-Host "OPENAI_API_KEY is NOT SET - the app will start but AI features will fail!" -ForegroundColor Red
}
Write-Host "------------------------------" -ForegroundColor Cyan
Write-Host ""

# ---------- Make sure Docker containers are running ----------
Write-Host "Ensuring Docker containers are up..." -ForegroundColor Cyan
docker start docquery-mysql 2>$null | Out-Null
docker start docquery-redis 2>$null | Out-Null
Start-Sleep -Seconds 2

# ---------- Run the app ----------
Write-Host "Starting DocQuery..." -ForegroundColor Cyan
mvn spring-boot:run
