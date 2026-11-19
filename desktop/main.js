/**
 * NDMA Tactical Command Center - Windows Desktop Application Main Process
 * Electron Runtime with Hardened Security, Managed Backend Process & Loading Screen
 */

const { app, BrowserWindow, Menu, ipcMain, shell, dialog, net } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn, execSync } = require('child_process');

const DEFAULT_SERVER_URL = 'http://localhost:8080';
let mainWindow = null;
let currentServerUrl = DEFAULT_SERVER_URL;
let backendProcess = null;

// Path to user-configured server URL file and logs
function getConfigFilePath() {
    return path.join(app.getPath('userData'), 'server-config.json');
}

function getLogDirectory() {
    const logDir = path.join(app.getPath('userData'), 'logs');
    if (!fs.existsSync(logDir)) {
        fs.mkdirSync(logDir, { recursive: true });
    }
    return logDir;
}

function logToFile(msg) {
    try {
        const logFile = path.join(getLogDirectory(), 'desktop.log');
        fs.appendFileSync(logFile, `[${new Date().toISOString()}] ${msg}\n`, 'utf8');
    } catch (e) {
        console.error('Failed to write to desktop log:', e.message);
    }
}

// Load saved server URL
function loadServerUrl() {
    // 1. Environment variable override
    if (process.env.DISASTER_SERVER_URL) {
        return process.env.DISASTER_SERVER_URL.trim().replace(/\/+$/, '');
    }

    // 2. Saved user config file
    try {
        const configPath = getConfigFilePath();
        if (fs.existsSync(configPath)) {
            const data = JSON.parse(fs.readFileSync(configPath, 'utf8'));
            if (data.serverUrl) {
                return data.serverUrl.trim().replace(/\/+$/, '');
            }
        }
    } catch (e) {
        logToFile('Could not read user server config: ' + e.message);
    }

    // 3. Fallback to default localhost
    return DEFAULT_SERVER_URL;
}

// Save user configured server URL
function saveServerUrl(url) {
    try {
        const cleanUrl = url.trim().replace(/\/+$/, '');
        const configPath = getConfigFilePath();
        fs.writeFileSync(configPath, JSON.stringify({ serverUrl: cleanUrl }, null, 2), 'utf8');
        currentServerUrl = cleanUrl;
        logToFile(`Saved new server URL target: ${cleanUrl}`);
        return true;
    } catch (e) {
        logToFile('Failed to write server config: ' + e.message);
        return false;
    }
}

// Locate candidate Spring Boot executable JAR
function findSpringJar() {
    if (process.env.DISASTER_JAR_PATH && fs.existsSync(process.env.DISASTER_JAR_PATH)) {
        return process.env.DISASTER_JAR_PATH;
    }
    const candidates = [
        path.join(process.resourcesPath || '', 'server', 'demo.jar'),
        path.join(process.resourcesPath || '', 'server', 'demo-0.0.1-SNAPSHOT.jar'),
        path.join(__dirname, 'server', 'demo.jar'),
        path.join(__dirname, 'server', 'demo-0.0.1-SNAPSHOT.jar'),
        path.join(__dirname, '..', 'target', 'demo-0.0.1-SNAPSHOT.jar'),
        path.join(__dirname, 'target', 'demo-0.0.1-SNAPSHOT.jar')
    ];
    for (const cand of candidates) {
        if (fs.existsSync(cand)) {
            return cand;
        }
    }
    return null;
}

// Spawn local Spring Boot backend if available and target is localhost
function startLocalBackendIfAvailable() {
    if (backendProcess) return;

    const isLocal = currentServerUrl.includes('localhost') || currentServerUrl.includes('127.0.0.1');
    if (!isLocal) {
        logToFile(`Server target is remote (${currentServerUrl}). Skipping local JAR spawn.`);
        return;
    }

    const jarPath = findSpringJar();
    if (!jarPath) {
        logToFile('No local Spring Boot JAR found in candidate paths. Expecting external or containerized backend.');
        return;
    }

    logToFile(`Starting managed local Spring Boot backend: ${jarPath}`);
    try {
        const backendLogPath = path.join(getLogDirectory(), 'backend.log');
        const backendOut = fs.openSync(backendLogPath, 'a');

        backendProcess = spawn('java', ['-jar', jarPath, '--server.port=8080'], {
            detached: false,
            stdio: ['ignore', backendOut, backendOut],
            windowsHide: true
        });

        backendProcess.on('error', (err) => {
            logToFile(`Backend process error: ${err.message}`);
            backendProcess = null;
        });

        backendProcess.on('exit', (code, signal) => {
            logToFile(`Backend process exited with code ${code}, signal ${signal}`);
            backendProcess = null;
        });

        logToFile(`Local backend spawned successfully with PID: ${backendProcess.pid}`);
    } catch (err) {
        logToFile(`Failed to spawn backend process: ${err.message}`);
    }
}

// Safely terminate managed backend process
function stopBackendProcess() {
    if (backendProcess && backendProcess.pid) {
        logToFile(`Terminating managed backend PID ${backendProcess.pid}...`);
        try {
            if (process.platform === 'win32') {
                execSync(`taskkill /pid ${backendProcess.pid} /t /f`);
            } else {
                backendProcess.kill('SIGTERM');
            }
            logToFile('Managed backend process terminated successfully.');
        } catch (e) {
            logToFile(`Managed backend termination note: ${e.message}`);
        }
        backendProcess = null;
    }
}

// Test connection to backend server
function checkServerHealth(url, timeoutMs = 2500) {
    return new Promise((resolve) => {
        const target = `${url}/actuator/health`;
        const fallbackTarget = `${url}/index.html`;

        const request = net.request({
            method: 'GET',
            url: target
        });

        let responded = false;
        const timer = setTimeout(() => {
            if (!responded) {
                responded = true;
                request.abort();
                checkFallbackIndex(fallbackTarget, resolve);
            }
        }, timeoutMs);

        request.on('response', (response) => {
            if (!responded) {
                responded = true;
                clearTimeout(timer);
                resolve(response.statusCode < 500);
            }
        });

        request.on('error', () => {
            if (!responded) {
                responded = true;
                clearTimeout(timer);
                checkFallbackIndex(fallbackTarget, resolve);
            }
        });

        request.end();
    });
}

function checkFallbackIndex(url, resolve) {
    const request = net.request({ method: 'GET', url });
    let done = false;
    const t = setTimeout(() => {
        if (!done) { done = true; request.abort(); resolve(false); }
    }, 1500);

    request.on('response', (res) => {
        if (!done) { done = true; clearTimeout(t); resolve(res.statusCode < 500); }
    });
    request.on('error', () => {
        if (!done) { done = true; clearTimeout(t); resolve(false); }
    });
    request.end();
}

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1440,
        height: 900,
        minWidth: 980,
        minHeight: 650,
        backgroundColor: '#050811',
        title: 'NDMA Tactical Command Center',
        icon: path.join(__dirname, 'assets', 'icon.ico'),
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true,
            sandbox: true,
            webSecurity: true,
            spellcheck: false
        }
    });

    buildApplicationMenu();
    loadApplicationWithLoadingScreen();

    // Prevent open window popup leaks; route externals to default OS browser
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        if (url.startsWith('http:') || url.startsWith('https:') || url.startsWith('tel:')) {
            shell.openExternal(url);
        }
        return { action: 'deny' };
    });

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

// Show loading screen, probe/spawn backend, and seamlessly navigate when ready
async function loadApplicationWithLoadingScreen() {
    currentServerUrl = loadServerUrl();
    logToFile(`Initiating application launch sequence. Target: ${currentServerUrl}`);

    // Load initial loading screen
    mainWindow.loadFile(path.join(__dirname, 'loading.html'));

    // Quick initial health probe
    let healthy = await checkServerHealth(currentServerUrl, 1500);
    if (healthy) {
        logToFile('Backend is already healthy. Loading portal immediately...');
        mainWindow.loadURL(`${currentServerUrl}/index.html`);
        return;
    }

    // If not healthy, attempt to start local backend
    startLocalBackendIfAvailable();

    // Poll health check every 1.5s up to 40 seconds
    const maxAttempts = 28;
    for (let i = 1; i <= maxAttempts; i++) {
        await new Promise(r => setTimeout(r, 1500));
        healthy = await checkServerHealth(currentServerUrl, 2000);
        if (healthy) {
            logToFile(`Backend health check succeeded on attempt ${i}. Transitioning to application portal...`);
            mainWindow.loadURL(`${currentServerUrl}/index.html`);
            return;
        }
    }

    logToFile('Backend health check timed out. Displaying connection fallback screen.');
    mainWindow.loadFile(path.join(__dirname, 'connection-error.html'));
}

// Native Application Menu
function buildApplicationMenu() {
    const template = [
        {
            label: 'NDMA Operations',
            submenu: [
                {
                    label: 'Mission Control (Admin)',
                    accelerator: 'CmdOrCtrl+1',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/admin-dashboard.html`)
                },
                {
                    label: 'Admin Notifications',
                    accelerator: 'CmdOrCtrl+Shift+1',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/admin-notifications.html`)
                },
                { type: 'separator' },
                {
                    label: 'Field Operations (Responder)',
                    accelerator: 'CmdOrCtrl+2',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/responder-dashboard.html`)
                },
                {
                    label: 'Responder Notifications',
                    accelerator: 'CmdOrCtrl+Shift+2',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/responder-notifications.html`)
                },
                { type: 'separator' },
                {
                    label: 'Civilian Safety Hub (Citizen)',
                    accelerator: 'CmdOrCtrl+3',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/citizen-dashboard.html`)
                },
                {
                    label: 'Citizen Notifications',
                    accelerator: 'CmdOrCtrl+Shift+3',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/citizen-notifications.html`)
                },
                { type: 'separator' },
                {
                    label: 'Gateway Portal (Login)',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/index.html`)
                },
                { type: 'separator' },
                { role: 'quit', label: 'Exit Command Center' }
            ]
        },
        {
            label: 'Server Connection',
            submenu: [
                {
                    label: 'Test Server Connection',
                    accelerator: 'CmdOrCtrl+T',
                    click: async () => {
                        const healthy = await checkServerHealth(currentServerUrl);
                        dialog.showMessageBox(mainWindow, {
                            type: healthy ? 'info' : 'warning',
                            title: 'Server Connection Telemetry',
                            message: `Target Server: ${currentServerUrl}`,
                            detail: healthy 
                                ? '✓ Status: Connected & Healthy\nSpring Boot Backend is actively processing requests.'
                                : '✗ Status: Unreachable\nPlease ensure the backend server is running or configure a different URL.'
                        });
                    }
                },
                {
                    label: 'Configure Target Server URL...',
                    accelerator: 'CmdOrCtrl+S',
                    click: () => {
                        mainWindow.loadFile(path.join(__dirname, 'connection-error.html'));
                    }
                },
                {
                    label: 'Reset to Localhost (8080)',
                    click: () => {
                        saveServerUrl(DEFAULT_SERVER_URL);
                        loadApplicationWithLoadingScreen();
                    }
                },
                { type: 'separator' },
                {
                    label: 'Open Application Logs Folder',
                    click: () => {
                        shell.openPath(getLogDirectory());
                    }
                }
            ]
        },
        {
            label: 'View',
            submenu: [
                { role: 'reload', label: 'Reload View' },
                { role: 'forceReload', label: 'Force Refresh' },
                { role: 'toggleDevTools', label: 'Developer Tools' },
                { type: 'separator' },
                { role: 'togglefullscreen', label: 'Toggle Fullscreen Mode' }
            ]
        },
        {
            label: 'Emergency Hotlines',
            submenu: [
                {
                    label: 'National Emergency: 112',
                    click: () => shell.openExternal('tel:112')
                },
                {
                    label: 'State Disaster Control: 1070',
                    click: () => shell.openExternal('tel:1070')
                },
                {
                    label: 'Medical Ambulance: 108',
                    click: () => shell.openExternal('tel:108')
                },
                { type: 'separator' },
                {
                    label: 'About NDMA Command Desktop',
                    click: () => {
                        dialog.showMessageBox(mainWindow, {
                            type: 'info',
                            title: 'About NDMA Tactical Command Center',
                            message: 'National Disaster Management & Alert System',
                            detail: `Version: 1.0.0\nDesktop Host: Windows x64\nTarget Server: ${currentServerUrl}\nOfficial Data: NDMA SACHET CAP 1.2 Feed\n\nFinal Year Academic Project Evaluation Build`
                        });
                    }
                }
            ]
        }
    ];

    const menu = Menu.buildFromTemplate(template);
    Menu.setApplicationMenu(menu);
}

// IPC Handlers
ipcMain.handle('get-server-url', () => currentServerUrl);

ipcMain.handle('set-server-url', (event, newUrl) => {
    return saveServerUrl(newUrl);
});

ipcMain.handle('test-connection', async (event, url) => {
    return checkServerHealth(url);
});

ipcMain.handle('reconnect-server', () => {
    loadApplicationWithLoadingScreen();
});

ipcMain.handle('get-app-version', () => app.getVersion());

ipcMain.handle('open-external', (event, url) => {
    if (url.startsWith('http:') || url.startsWith('https:') || url.startsWith('tel:')) {
        shell.openExternal(url);
    }
});

// App Lifecycle
app.whenReady().then(createWindow);

app.on('before-quit', () => {
    stopBackendProcess();
});

app.on('window-all-closed', () => {
    stopBackendProcess();
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
    }
});
