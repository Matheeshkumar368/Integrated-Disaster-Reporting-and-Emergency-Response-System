@echo off
title Configure Windows Firewall for Disaster Response System
net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Administrative privileges required. Right-click and select 'Run as administrator'.
    pause
    exit /b 1
)
netsh advfirewall firewall add rule name="DisasterResponseSystem_Port8080" dir=in action=allow protocol=TCP localport=8080 profile=private,domain
echo [SUCCESS] Windows Firewall port 8080 opened for local Wi-Fi / LAN devices.
pause
