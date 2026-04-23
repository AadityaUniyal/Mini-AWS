@echo off
setlocal enabledelayedexpansion
title MiniCloud -- Java Cloud Monolith
echo ============================================================
echo   MiniCloud -- Java Cloud Platform (Modular Monolith)
echo   Optimized for Local Development ^& Laptop Use
echo ============================================================
echo.

set "ROOT=%~dp0"
set "MODE=WEB"

REM ── Check CLI Arguments ───────────────────────────────────────────────────
if /i "%~1"=="web" (
    set "MODE=WEB"
    goto START_BUILD
)
if /i "%~1"=="desktop" (
    set "MODE=DESKTOP"
    goto START_BUILD
)

REM ── Interactive Mode Selection ────────────────────────────────────────────
echo  Select Startup Mode:
echo    [1] Production Web Service (Headless API + Tray Icon)
echo    [2] Desktop UI (Swing Dashboard + Embedded API)
set /p CHOICE="  Enter choice [1-2] (Default 1): "

if "%CHOICE%"=="2" (
    set "MODE=DESKTOP"
) else (
    set "MODE=WEB"
)

:START_BUILD
echo.
echo  Selected Mode: %MODE%
echo.

REM ── Build ─────────────────────────────────────────────────────────────────
echo  Building MiniCloud...
cd /d "%ROOT%"
call "%ROOT%mvnw.cmd" clean package -DskipTests -pl minicloud-api -am -q
if errorlevel 1 (
    echo.
    echo  BUILD FAILED. Please check the maven output.
    pause
    exit /b 1
)
echo  Build successful!
echo.

REM ── JVM Args (Optimized for Laptop) ───────────────────────────────────────
set "JVM_OPTS=-Xmx512m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

REM ── Start Monolith ────────────────────────────────────────────────────────
echo  Starting MiniCloud in %MODE% mode...
start "MiniCloud" cmd /k "cd /d "%ROOT%minicloud-api" && java %JVM_OPTS% -jar target/minicloud-api-1.0.0.jar --mode=%MODE%"

REM ── Wait for Ready ────────────────────────────────────────────────────────
echo  Waiting for platform to be ready...
set RETRIES=0
:WAIT_LOOP
timeout /t 3 /nobreak >nul
set /a RETRIES+=1
curl -s -o nul -w "%%{http_code}" http://localhost:8080/actuator/health 2>nul | findstr "200" >nul
if errorlevel 1 (
    if !RETRIES! geq 20 (
        echo  WARNING: Platform taking longer than expected to start.
        goto FINAL_STEPS
    )
    echo  Still loading... [!RETRIES!/20]
    goto WAIT_LOOP
)
echo  MiniCloud is LIVE!
echo.

:FINAL_STEPS
if /i "%MODE%"=="WEB" (
    echo  MiniCloud is running as a background service.
    echo  Access endpoints at:
    echo    - H2 Console:  http://localhost:8080/h2-console
    echo    - Swagger UI:  http://localhost:8080/swagger-ui.html
    echo    - Health:      http://localhost:8080/actuator/health
    echo.
    echo  (A system tray icon is also available for management)
) else (
    echo  MiniCloud Desktop UI is launching...
    echo  The pure Java management dashboard will appear shortly.
)

echo.
echo ============================================================
echo   MiniCloud is running.
echo   Press any key to close this launcher.
echo ============================================================
pause
