# ============================================================
#  RagAgent one-click dev launcher
#  Backend : Spring Boot (http://localhost:8080) + local Embedding (8001, auto-started by backend)
#  Frontend: Vite storefront  (http://localhost:5173)
#  Admin   : Vite admin console (http://localhost:5174)
#  Usage   : powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1
# ============================================================

$ErrorActionPreference = 'Stop'
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Join-Path $rootDir 'server'
$webDir    = Join-Path $rootDir 'web'
$adminDir  = Join-Path $rootDir 'admin'

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RagAgent One-Click Start" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# ---------- 1. preflight ----------
Write-Host "[1/6] Checking environment..." -ForegroundColor Green
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
Write-Host "[2/6] Checking frontend deps..." -ForegroundColor Green
foreach ($dir in @(@{ Path = $webDir;   Name = "storefront" },
                   @{ Path = $adminDir; Name = "admin" })) {
    if (Test-Path (Join-Path $dir.Path 'node_modules')) {
        Write-Host "  $($dir.Name): node_modules exists, skipping npm install" -ForegroundColor Green
    } else {
        Write-Host "  $($dir.Name): first run, running npm install ..." -ForegroundColor Yellow
        Push-Location $dir.Path
        npm install
        Pop-Location
    }
}

# ---------- 3. port check ----------
Write-Host ""
Write-Host "[3/6] Checking ports..." -ForegroundColor Green
$portBackend  = 8080
$portFrontend = 5173
$portAdmin    = 5174
function Test-PortListen($port) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    return [bool]$conn
}
if (Test-PortListen $portBackend)  { Write-Host "  [info] port $portBackend busy, backend may already run (reuse)." -ForegroundColor Yellow }
if (Test-PortListen $portFrontend) { Write-Host "  [info] port $portFrontend busy, storefront may already run (reuse)." -ForegroundColor Yellow }
if (Test-PortListen $portAdmin)    { Write-Host "  [info] port $portAdmin busy, admin may already run (reuse)." -ForegroundColor Yellow }

# ---------- 4. backend ----------
Write-Host ""
Write-Host "[4/6] Starting backend (Spring Boot + auto Embedding)..." -ForegroundColor Green
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

# ---------- 5. storefront frontend ----------
Write-Host ""
Write-Host "[5/6] Starting storefront (Vite)..." -ForegroundColor Green
$frontend = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "npm run dev" -WorkingDirectory $webDir -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 3

# ---------- 6. admin frontend ----------
Write-Host ""
Write-Host "[6/6] Starting admin console (Vite)..." -ForegroundColor Green
$admin = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "npm run dev" -WorkingDirectory $adminDir -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  All services started." -ForegroundColor Cyan
Write-Host "  Storefront : http://localhost:$portFrontend" -ForegroundColor Green
Write-Host "  Admin      : http://localhost:$portAdmin  (login: admin)" -ForegroundColor Green
Write-Host "  Backend    : http://localhost:$portBackend" -ForegroundColor Green
Write-Host "  Images     : http://localhost:$portBackend/images/..." -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Backend PID: $($backend.Id) | Storefront PID: $($frontend.Id) | Admin PID: $($admin.Id)" -ForegroundColor DarkGray
Write-Host "  Close this window or Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host ""
