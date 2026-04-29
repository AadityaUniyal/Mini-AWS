@echo off
REM ============================================================
REM  MiniCloud Desktop Application Launcher
REM  Pure Java Swing Desktop - No Browser Required
REM ============================================================

echo.
echo ========================================
echo   MiniCloud Desktop Application
echo ========================================
echo.

REM Check if MySQL is running
echo [1/4] Checking MySQL service...
sc query MySQL80 | find "RUNNING" >nul
if errorlevel 1 (
    echo [ERROR] MySQL service is not running!
    echo.
    echo Please start MySQL service:
    echo   net start MySQL80
    echo.
    echo Or start it from Services.msc
    pause
    exit /b 1
)
echo [OK] MySQL service is running

REM Check if database exists
echo [2/4] Checking database connection...
mysql -u root -p -e "USE minicloud_db; SELECT 'Database OK' AS status;" 2>nul
if errorlevel 1 (
    echo [WARNING] Database 'minicloud_db' not found or connection failed
    echo.
    echo Please run the MySQL Workbench setup script:
    echo   1. Open MySQL Workbench
    echo   2. Connect to localhost
    echo   3. Open file: mysql-workbench-setup.sql
    echo   4. Execute the entire script
    echo.
    pause
)

REM Build the project
echo [3/4] Building project...
call mvnw.cmd clean package -DskipTests -pl minicloud-api -am
if errorlevel 1 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)
echo [OK] Build successful

REM Run the desktop application
echo [4/4] Launching desktop application...
echo.
echo ========================================
echo   Starting MiniCloud Desktop UI...
echo ========================================
echo.

call mvnw.cmd spring-boot:run -pl minicloud-api -Dfile.encoding=UTF-8

pause
