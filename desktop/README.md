# NDMA Command Center — Windows Desktop Application
**National Disaster Management & Alert System — Electron Desktop Client**

---

## 1. Overview
The Desktop client provides a dedicated, native Windows executable (`.exe` / installer) for the **NDMA Tactical Command Center**, tailored for command room monitors, field laptops, and offline-resilient operational monitoring.

### Architecture Highlights
- **100% Shared Backend:** Connects to the same Spring Boot / MySQL server. No duplicate database or local user discrepancies.
- **Configurable Server Target:** Seamlessly toggle between local development (`http://localhost:8080`), local area network (`http://192.168.x.x:8080`), or hosted cloud deployment (`https://disaster.gov.in`).
- **Connection Pre-Flight Guard:** Performs automated health checks on startup. If the backend is temporarily offline, presents a graceful diagnostic screen with one-click retry and URL configuration instead of a blank white screen.
- **Strict Electron Security:**
  - `contextIsolation: true`
  - `nodeIntegration: false`
  - `sandbox: true`
  - External links (`tel:112`, Google Maps) routed to default OS browser via `shell.openExternal`.

---

## 2. Prerequisites
- **Node.js:** v18+ (verified with Node.js v24)
- **Backend:** Java Spring Boot server running locally or hosted on cloud.

---

## 3. Installation & Local Development

From the project root:
```powershell
# 1. Navigate to desktop directory
cd desktop

# 2. Install dependencies (Electron & electron-builder)
npm install

# 3. Launch Desktop Command Center in Development Mode
npm start
```

---

## 4. Packaging the Windows Executable

To produce a portable `.exe` or an NSIS Windows Installer:

```powershell
cd desktop

# Build standalone unpacked directory (quickest test)
npm run pack

# Build full Windows installer (NSIS & Portable x64)
npm run build:win
```

Output binaries will be placed in `desktop/dist/`:
- `NDMA Command Center Setup 1.0.0.exe` (NSIS Installer)
- `NDMA Command Center 1.0.0.exe` (Portable Standalone Executable)

---

## 5. Configuring Target Server URL

### Method A: In-App Menu
1. From the top application menu, select **Server Connection ➔ Configure Target Server URL...**
2. Enter the hosted domain (e.g. `https://your-production-server.com`) or LAN IP.
3. Click **Save & Reconnect**.

### Method B: Environment Variable
Launch with `DISASTER_SERVER_URL`:
```powershell
$env:DISASTER_SERVER_URL = "https://disaster-management.example.com"
npm start
```

### Method C: Stored JSON Configuration
The client automatically saves the server endpoint in:
`%APPDATA%\ndma-command-center-desktop\server-config.json`
```json
{
  "serverUrl": "https://disaster-management.example.com"
}
```

