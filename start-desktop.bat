@echo off
REM ============================================================
REM  MiniCloud Desktop Application Launcher (Neon PostgreSQL)
REM  Pure Java Swing Desktop - No local MySQL Required
REM ============================================================

echo.
echo ========================================
echo   MiniCloud Desktop Application
echo ========================================
echo.

REM Build the project
echo [1/2] Building project...
call mvnw.cmd clean package -DskipTests -pl minicloud-api -am
if errorlevel 1 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)
echo [OK] Build successful

REM Run the desktop application
echo [2/2] Launching desktop application...
echo.
echo ========================================
echo   Starting MiniCloud Desktop UI...
echo ========================================
echo.

call mvnw.cmd spring-boot:run -pl minicloud-api -Dfile.encoding=UTF-8 --mode=DESKTOP

pause
