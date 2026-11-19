@echo off
title NDMA Disaster Command Center - Windows Desktop Build
echo ===============================================================================
echo   INTEGRATED DISASTER REPORTING AND EMERGENCY RESPONSE COORDINATION SYSTEM
echo   Windows Desktop Application (EXE) Packaging Pipeline
echo ===============================================================================
cd /d "%~dp0"

echo.
echo [1/4] Compiling Spring Boot Executable JAR...
call .\mvnw.cmd package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Spring Boot JAR packaging failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/4] Syncing Backend Artifact to Desktop Server Directory...
if not exist "desktop\server" mkdir "desktop\server"
copy /Y "target\demo-0.0.1-SNAPSHOT.jar" "desktop\server\demo.jar"

cd desktop

echo.
echo [3/4] Verifying Electron Runtime Dependencies in .\desktop ...
call npm install --no-audit --prefer-offline
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to install Node.js dependencies. Ensure Node and npm are installed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [4/4] Packaging Windows Desktop Executables (NSIS Setup & Portable EXE)...
call npm run build:win
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Electron build failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ===============================================================================
echo [SUCCESS] Windows Desktop application successfully packaged!
echo Binaries available in: %~dp0desktop\dist\
echo   - NSIS Setup:   desktop\dist\NDMA Command Center Setup 1.0.0.exe
echo   - Portable EXE: desktop\dist\NDMA Command Center 1.0.0.exe
echo ===============================================================================
pause
