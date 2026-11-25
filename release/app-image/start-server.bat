@echo off
title Disaster Response System - Console Server
cd /d "%~dp0"
if exist "runtime\bin\java.exe" (
    set "JAVA_CMD=runtime\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)
"%JAVA_CMD%" -XX:TieredStopAtLevel=1 -Dserver.port=8080 -Dserver.address=0.0.0.0 -jar server\demo-0.0.1-SNAPSHOT.jar
pause
