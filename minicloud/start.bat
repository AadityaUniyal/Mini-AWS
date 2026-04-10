@echo off
setlocal enabledelayedexpansion
echo =====================================
echo   MiniCloud — Java Cloud Platform
echo =====================================
echo.

set "ROOT=%~dp0"

REM Build all modules
echo [1/3] Building project...
cd /d "%ROOT%"
call "%ROOT%mvnw.cmd" clean install -q -DskipTests
if errorlevel 1 (
    echo BUILD FAILED. Check Maven output above for errors.
    pause
    exit /b 1
)
echo Build successful!
echo.

REM Start API server in a new window (background)
echo [2/3] Starting MiniCloud API on port 8080...
start "MiniCloud API Server" cmd /k "cd /d "%ROOT%minicloud-api" && "%ROOT%mvnw.cmd" spring-boot:run"

echo Waiting 15 seconds for API to start...
timeout /t 15 /nobreak > nul

REM Check if API is up (optional helper)
echo.
echo API is likely ready. Opening Swagger UI...
start http://localhost:8080/swagger-ui.html

REM Launch Swing Dashboard
echo [3/3] Launching Swing Dashboard...
cd /d "%ROOT%minicloud-dashboard"
call "%ROOT%mvnw.cmd" exec:java -Dexec.mainClass="com.minicloud.ui.SwingMain"

echo.
echo MiniCloud Dashboard session ended.
pause
