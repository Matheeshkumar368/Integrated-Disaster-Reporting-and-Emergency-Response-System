@echo off
title NDMA Disaster Command Center - Windows Desktop Build
echo ===============================================================================
echo   INTEGRATED DISASTER REPORTING AND EMERGENCY RESPONSE COORDINATION SYSTEM
echo   Windows Desktop Application (EXE) Packaging Pipeline
echo ===============================================================================
cd /d "%~dp0desktop"

echo.
echo [1/3] Verifying Electron runtime dependencies in .\desktop ...
call npm install --no-audit --prefer-offline
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to install Node.js dependencies. Ensure Node and npm are installed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Building Windows Executables (NSIS Setup & Portable EXE)...
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
