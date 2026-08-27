@echo off
REM Local Embedding Service Starter (Chinese-CLIP)
REM Port: 8001  Model cache: <dir>\models
REM Docs: docs/Day16-本地多模态Embedding服务.md

chcp 65001 >nul 2>&1
cd /d "%~dp0"

set "HF_HOME=%~dp0models"
set "HF_HUB_CACHE=%~dp0models"
set "TRANSFORMERS_OFFLINE=0"
set "HF_ENDPOINT=https://hf-mirror.com"
set "PYTHONUNBUFFERED=1"

echo ============================================================
echo  Local Embedding Service Starter
echo  Model: OFA-Sys/chinese-clip-vit-base-patch16
echo ============================================================
echo  Working dir : %cd%
echo  HF_HOME     : %HF_HOME%
echo.

echo [Step 0/3] Checking Python...
where python >nul 2>&1
if errorlevel 1 (
    echo.
    echo  [ERROR] Python not found in PATH!
    echo  Install Python 3.10+ and CHECK "Add to PATH" during install.
    echo.
    pause
    exit /b 1
)
python --version
echo       Python OK
echo.

if not exist "venv\Scripts\activate.bat" (
    echo [Step 1/3] Creating virtual environment (first run)...
    python -m venv venv
    if errorlevel 1 (
        echo  [ERROR] venv creation failed
        pause
        exit /b 1
    )
    echo       venv created
)

echo [Step 2/3] Checking dependencies...
call venv\Scripts\activate.bat
if errorlevel 1 (
    echo  [ERROR] venv activation failed. Try: delete venv folder and rerun.
    pause
    exit /b 1
)

python -c "import fastapi, transformers, torch" >nul 2>&1
if errorlevel 1 (
    echo       Installing dependencies (first run, ~5-10 min)...
    pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/
    if errorlevel 1 (
        echo  [ERROR] pip install failed. Check network.
        pause
        exit /b 1
    )
    echo       Dependencies installed
) else (
    echo       Dependencies OK
)

echo.
echo [Step 3/3] Starting Embedding Service ...
echo  URL:        http://localhost:8001
echo  Health:     curl http://localhost:8001/health
echo  Stop:       Press Ctrl+C
echo  Logs:       window below
echo ============================================================
echo.

python -m uvicorn main:app --host 0.0.0.0 --port 8001

if errorlevel 1 (
    echo ============================================================
    echo  [ERROR] Uvicorn exited with code %errorlevel%
    echo  Copy the Python traceback above and send it to me
    echo ============================================================
    pause
)

endlocal