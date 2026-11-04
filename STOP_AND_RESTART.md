# 🛑 Stop and Restart Application

## The Problem

```
Port 8080 was already in use.
```

The application is already running! You need to stop it first.

---

## Solution - Stop the Running Process

### Option 1: Find and Kill Process (Windows)

```bash
# Find what's using port 8080
netstat -ano | findstr :8080

# You'll see something like:
# TCP    0.0.0.0:8080    0.0.0.0:0    LISTENING    12345

# Kill the process (replace 12345 with the actual PID)
taskkill /PID 12345 /F
```

### Option 2: Use PowerShell

```powershell
# Find and kill process on port 8080
Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force
```

### Option 3: Stop in VS Code Terminal

If you started it in VS Code terminal:
1. Click on the terminal where it's running
2. Press **Ctrl + C**
3. Wait for it to stop

---

## After Stopping, Restart

```bash
./mvnw spring-boot:run
```

Wait for:
```
Started DisasterManagementApplication
```

---

## Quick Commands

### Stop Process on Port 8080:
```bash
netstat -ano | findstr :8080
taskkill /PID [PID_NUMBER] /F
```

### Start Application:
```bash
./mvnw spring-boot:run
```

### Check if Running:
```bash
netstat -ano | findstr :8080
```

---

## Then Test

1. Open: http://localhost:8080
2. Login as admin
3. Check if disasters show
4. Press F12 to see console logs

---

**Stop the old process first, then restart!** 🔄
