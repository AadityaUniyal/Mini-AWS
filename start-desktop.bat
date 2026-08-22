@echo off
REM ============================================================
REM  MiniCloud Desktop Application Launcher
REM  Pure Java Swing Desktop + Embedded API
REM ============================================================

echo.
echo ========================================
echo   MiniCloud Desktop Application
echo ========================================
echo.

REM ── Check & Resolve JAVA_HOME ───────────────────────────────────────────────
if not exist "%JAVA_HOME%\bin\java.exe" (
    if exist "C:\Program Files\Java\jdk-17.0.20\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.20"
    ) else if exist "C:\Program Files\Java\latest\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\latest"
    )
)

echo Launching MiniCloud Desktop UI...
call mvnw.cmd -pl minicloud-api spring-boot:run -Dfile.encoding=UTF-8 -Dspring-boot.run.arguments="--mode=DESKTOP"

pause
