@echo off
:: ============================================================================
:: Disaster Management System - Windows Firewall LAN Access Setup
:: Adds inbound TCP rule for Port 8080 so other laptops on Wi-Fi/LAN can connect.
:: Run as Administrator.
:: ============================================================================

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ============================================================================
    echo [REQUESTING ADMINISTRATOR PERMISSION]
    echo Elevating privileges to configure Windows Firewall...
    echo ============================================================================
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo ============================================================================
echo  CONFIGURING WINDOWS FIREWALL FOR DISASTER MANAGEMENT CENTRAL SERVER
echo ============================================================================
echo.

:: Remove old rule if present
netsh advfirewall firewall delete rule name="DisasterManagement_Port8080" >nul 2>&1

:: Add inbound rule for private and domain networks (NOT public internet)
netsh advfirewall firewall add rule name="DisasterManagement_Port8080" dir=in action=allow protocol=TCP localport=8080 profile=private,domain description="Allows incoming LAN/Wi-Fi traffic to Central Disaster Management Server on Port 8080"

if %errorLevel% equ 0 (
    echo [SUCCESS] Inbound firewall rule active for TCP Port 8080.
    echo Connected laptops and phones on your Wi-Fi network can now reach the server!
) else (
    echo [ERROR] Failed to add firewall rule. Error code: %errorLevel%
)

echo.
echo ============================================================================
pause
