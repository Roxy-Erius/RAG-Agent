# ============================================================
#  RagAgent one-click dev launcher
#  Backend : Spring Boot (http://localhost:8080) + local Embedding (8001, auto-started by backend)
#  Frontend: Vite (http://localhost:5173)
#  Usage   : powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1
# ============================================================

$ErrorActionPreference = 'Stop'
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Join-Path $rootDir 'server'
$webDir    = Join-Path $rootDir 'web'

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RagAgent One-Click Start" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# ---------- 1. preflight ----------
Write-Host "[1/5] Checking environment..." -ForegroundColor Green
$missing = @()
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { $missing += "node" }
if (-not (Get-Command npm -ErrorAction SilentlyContinue))  { $missing += "npm" }

$mvn = $null
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnCmd) { $mvn = $mvnCmd.Source }
if (-not $mvn) {
    $candidates = @("D:\develop\apache-maven-3.9.4\bin\mvn.cmd", "C:\Program Files\apache-maven\bin\mvn.cmd")
    foreach ($c in $candidates) {
        if (Test-Path $c) { $mvn = $c; break }
    }
}
if (-not $mvn) { $missing += "mvn" }

if ($missing.Count -gt 0) {
    Write-Host "  [ERROR] Missing: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "         Please install and add to PATH." -ForegroundColor Yellow
    exit 1
}
Write-Host "  Env OK (node/npm/mvn)" -ForegroundColor Green

# ---------- 2. frontend deps ----------
Write-Host ""
Write-Host "[2/5] Checking frontend deps..." -ForegroundColor Green
if (Test-Path (Join-Path $webDir 'node_modules')) {
    Write-Host "  node_modules exists, skipping npm install" -ForegroundColor Green
} else {
    Write-Host "  First run, running npm install ..." -ForegroundColor Yellow
    Push-Location $webDir
    npm install
    Pop-Location
}

# ---------- 3. port check ----------
Write-Host ""
Write-Host "[3/5] Checking ports..." -ForegroundColor Green
$portBackend = 8080
$portFrontend = 5173
function Test-PortListen($port) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    return [bool]$conn
}
if (Test-PortListen $portBackend) { Write-Host "  [info] port $portBackend busy, backend may already run (reuse)." -ForegroundColor Yellow }
if (Test-PortListen $portFrontend) { Write-Host "  [info] port $portFrontend busy, frontend may already run (reuse)." -ForegroundColor Yellow }

# ---------- 4. backend ----------
Write-Host ""
Write-Host "[4/5] Starting backend (Spring Boot + auto Embedding)..." -ForegroundColor Green
Push-Location $serverDir
$backend = Start-Process -FilePath $mvn -ArgumentList "spring-boot:run" -PassThru -WindowStyle Hidden
Pop-Location
Write-Host "  Backend launched (PID: $($backend.Id)), waiting for port $portBackend ..." -ForegroundColor Yellow

$backendReady = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if (Test-PortListen $portBackend) { $backendReady = $true; break }
}
if ($backendReady) {
    Write-Host "  Backend ready: http://localhost:$portBackend" -ForegroundColor Green
} else {
    Write-Host "  [warn] timeout, backend may still be starting or failed." -ForegroundColor Yellow
    Write-Host "         Check log: server\logs\..." -ForegroundColor Yellow
}

# ---------- 5. frontend ----------
Write-Host ""
Write-Host "[5/5] Starting frontend (Vite)..." -ForegroundColor Green
$npmCmd = (Get-Command npm.cmd -ErrorAction SilentlyContinue) | Select-Object -ExpandProperty Source
if (-not $npmCmd) { $npmCmd = (Get-Command npm -ErrorAction SilentlyContinue) | Select-Object -ExpandProperty Source }
Push-Location $webDir
$frontend = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "npm run dev" -WorkingDirectory $webDir -PassThru -WindowStyle Hidden
Pop-Location
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  All services started." -ForegroundColor Cyan
Write-Host "  Frontend: http://localhost:$portFrontend" -ForegroundColor Green
Write-Host "  Backend : http://localhost:$portBackend" -ForegroundColor Green
Write-Host "  Images  : http://localhost:$portBackend/images/..." -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Backend PID: $($backend.Id) | Frontend PID: $($frontend.Id)" -ForegroundColor DarkGray
Write-Host "  Close this window or Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host ""
