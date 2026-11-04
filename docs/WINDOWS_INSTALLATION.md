# WINDOWS DESKTOP INSTALLATION & PACKAGING GUIDE
## NDMA Tactical Command Center (Desktop Edition)

**Target Operating System:** Windows 10 / Windows 11 (64-bit)  
**Desktop Framework:** Electron 33.4.11 | Electron-Builder 25.1.8  
**Architecture:** x64  
**Package Formats:** NSIS Windows Installer (`.exe`), Standalone Portable (`.exe`), Unpacked Directory  
**Status:** **PACKAGED & VERIFIED (Zero Compilation / Packaging Errors)**

---

## 1. GENERATED ARTIFACTS OVERVIEW

The desktop build pipeline creates production binaries in the `desktop/dist/` directory:

| Artifact Name | Format | Size | Description & Deployment Target |
| :--- | :---: | :---: | :--- |
| **`NDMA Command Center Setup 1.0.0.exe`** | NSIS Installer | **81.72 MB** | Standard Windows installer with custom directory selection, Desktop icon, and Start Menu shortcuts. Recommended for workstation deployment. |
| **`NDMA Command Center 1.0.0.exe`** | Portable Executable | **81.50 MB** | Standalone single-file executable requiring zero installation. Can run directly from USB drives or field responder laptops. |
| **`win-unpacked/NDMA Command Center.exe`** | Unpacked Directory | **188 MB** | Full pre-extracted application binary bundle ready for immediate local execution or enterprise software push. |

---

## 2. SYSTEM REQUIREMENTS

* **Operating System:** Windows 10 (Build 1809+) or Windows 11 (64-bit)
* **Processor:** 1.8 GHz dual-core or higher (x64 architecture)
* **Memory (RAM):** 4 GB minimum (8 GB recommended for simultaneous high-res tactical map rendering)
* **Disk Space:** 350 MB free disk space
* **Network:** TCP/IP connection to the Disaster Management Backend (localhost or remote server)
* **Display:** 1280 x 720 minimum (1440 x 900 or 1920 x 1080 recommended)

---

## 3. INSTALLATION PROCEDURES

### Method A: Interactive NSIS Installer (Recommended)
1. Double-click **`NDMA Command Center Setup 1.0.0.exe`**.
2. If prompted by Windows SmartScreen ("Unknown Publisher"), click **More info** $\to$ **Run anyway** (standard for non-EV academic packages).
3. The setup wizard displays:
   * **Destination Folder:** Defaults to `C:\Users\<User>\AppData\Local\Programs\ndma-command-center-desktop`. You may change this directory if desired.
   * **Shortcuts:** Automatically creates a desktop icon and a Start Menu entry under `NDMA Operations Command`.
4. Click **Install**. Installation completes in approximately 5 seconds.
5. Check **Run NDMA Command Center** and click **Finish**.

### Method B: Portable Single-File Executable
1. Copy **`NDMA Command Center 1.0.0.exe`** to any directory or USB drive.
2. Double-click the file to launch immediately. No registry modifications or system files are written.

---

## 4. TARGET BACKEND CONFIGURATION & CONNECTIVITY

The Windows Desktop application is decoupled from the backend and can connect to a local server or a remote cloud deployment:

### Default Connection
On launch, the desktop application connects to:
```
http://localhost:8080
```

### Changing the Backend Server URL
1. From the desktop application top menu, select:
   * **Server Connection** $\to$ **Configure Target Server URL...** (or press `Ctrl+S`).
2. Alternatively, if the backend server is unreachable, the application automatically displays the built-in **Connection Error & Configuration Screen** (`connection-error.html`).
3. Enter the production backend URL (e.g., `https://disaster-response.gov.in` or `http://192.168.1.50:8080`).
4. Click **Test & Connect**. The application performs a live pre-flight latency health check to `/actuator/health`.
5. Upon confirmation, the setting is persisted to:
   ```
   %APPDATA%\ndma-command-center-desktop\server-config.json
   ```
6. To reset to default localhost, select **Server Connection** $\to$ **Reset to Localhost (8080)**.

### Environment Variable Override
For automated enterprise or test environments, specify the backend URL via environment variable before launch:
```powershell
$env:DISASTER_SERVER_URL = "http://192.168.1.100:8080"
& "desktop\dist\NDMA Command Center 1.0.0.exe"
```

---

## 5. HEALTH CHECKS & ERROR RECOVERY

* **Connection Error Fallback:** If the backend server fails or disconnects mid-session, the desktop app traps the failure and renders a high-contrast dark emergency screen detailing the unreachable URL.
* **Connection Telemetry:** Evaluators can press `Ctrl+T` anytime to display a native diagnostic dialog reporting the backend status, response latency, and HTTP connectivity.
* **Developer Tools:** Press `Ctrl+Shift+I` or select **View** $\to$ **Developer Tools** to inspect network traffic, console output, and memory usage.

---

## 6. HARDENED ELECTRON SECURITY ARCHITECTURE

The desktop application complies with official Electron Security Guidelines:

1. **Context Isolation Enabled:** `contextIsolation: true` prevents untrusted web content from accessing Electron internal APIs.
2. **Node Integration Disabled:** `nodeIntegration: false` ensures that scripts running inside the browser window cannot execute arbitrary Node.js commands.
3. **Sandbox Active:** `sandbox: true` confines rendering processes to standard OS sandboxing limits.
4. **Secure IPC Bridge:** Only safe, enumerated methods (`getServerUrl`, `setServerUrl`, `testConnection`, `openExternal`) are exposed via `preload.js` through `contextBridge`.
5. **Popup Leak Prevention:** All external links (`http:`, `https:`, `tel:`) are intercepted by `webContents.setWindowOpenHandler` and forwarded to the default operating system browser, preventing internal navigation hijack.
6. **Zero Embedded Secrets:** The desktop package contains zero database passwords, JWT secrets, or administrative credentials. All authentication is performed via runtime REST calls.

---

## 7. BUILDING FROM SOURCE (REPRODUCIBILITY)

To recompile the desktop packages from scratch:

```powershell
# 1. Navigate to desktop folder
cd desktop

# 2. Install dependencies (if not already installed)
npm install

# 3. Generate 256x256 Windows Icon (if assets modified)
powershell -ExecutionPolicy Bypass -File ..\scripts\build_desktop_icon.ps1

# 4. Build NSIS and Portable packages
npm run build:win
```

*Output directory: `desktop/dist/`*

