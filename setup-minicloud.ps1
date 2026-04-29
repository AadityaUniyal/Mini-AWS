$ErrorActionPreference = "Stop"
$WorkspacePath = (Get-Item -Path ".\").FullName
$DataDir = Join-Path $WorkspacePath "minicloud-data"
$MysqlDir = Join-Path $DataDir "mysql-server"
$MysqlZip = Join-Path $DataDir "mysql.zip"

If (!(Test-Path $DataDir)) {
    New-Item -ItemType Directory -Path $DataDir | Out-Null
}

If (!(Test-Path $MysqlDir)) {
    Write-Host "Downloading MySQL Server (this may take a few minutes)..."
    Invoke-WebRequest -Uri "https://cdn.mysql.com//archives/mysql-8.0/mysql-8.0.36-winx64.zip" -OutFile $MysqlZip
    
    Write-Host "Extracting MySQL Server..."
    Expand-Archive -Path $MysqlZip -DestinationPath $DataDir -Force
    
    Rename-Item -Path (Join-Path $DataDir "mysql-8.0.36-winx64") -NewName "mysql-server"
    Remove-Item $MysqlZip
    
    Write-Host "Initializing Database (blank root password)..."
    $mysqld = Join-Path $MysqlDir "bin\mysqld.exe"
    & $mysqld --initialize-insecure --console
}

$running = Get-Process mysqld -ErrorAction SilentlyContinue
If (!$running) {
    Write-Host "Starting MySQL Server locally..."
    $mysqld = Join-Path $MysqlDir "bin\mysqld.exe"
    Start-Process -FilePath $mysqld -WindowStyle Hidden
    Start-Sleep -Seconds 5
} Else {
    Write-Host "MySQL Server is already running."
}

Write-Host "Creating 'minicloud' database..."
$mysql = Join-Path $MysqlDir "bin\mysql.exe"
& $mysql -u root -e "CREATE DATABASE IF NOT EXISTS minicloud CHARACTER SET utf8mb4;"

Write-Host "MySQL Setup Complete! You can now start the app."
