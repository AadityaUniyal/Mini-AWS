@echo off
REM Start MiniCloud in Desktop Mode (Swing UI)
REM No browser, no localhost - pure Java Swing interface

echo ============================================================
echo   MiniCloud - Starting in Desktop Mode
echo   Pure Java Swing UI - No Web Browser Required
echo ============================================================
echo.

REM Set environment variable to force desktop mode
set MINICLOUD_MODE=DESKTOP

echo Building MiniCloud...
call mvnw.cmd clean package -DskipTests -q

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Build successful!
echo.
echo Starting MiniCloud Desktop UI...
echo - MySQL Database: localhost:3306/minicloud
echo - Swing Dashboard will open automatically
echo - No web browser needed
echo.

REM Start in desktop mode
cd minicloud-api
call ..\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--minicloud.mode=DESKTOP"

pause
