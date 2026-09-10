@echo off
REM RagAgent one-click launcher (double-click)
REM Real logic is in start-dev.ps1
chcp 65001 >nul 2>&1
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1"
pause
