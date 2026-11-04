/**
 * NDMA Tactical Command Center - Windows Desktop Application Main Process
 * Electron Runtime with Hardened Security & Configurable Backend Target
 */

const { app, BrowserWindow, Menu, ipcMain, shell, dialog, net } = require('electron');
const path = require('path');
const fs = require('fs');

const DEFAULT_SERVER_URL = 'http://localhost:8080';
let mainWindow = null;
let currentServerUrl = DEFAULT_SERVER_URL;

// Path to user-configured server URL file
function getConfigFilePath() {
    return path.join(app.getPath('userData'), 'server-config.json');
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
        console.warn('Could not read user server config:', e.message);
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
        return true;
    } catch (e) {
        console.error('Failed to write server config:', e.message);
        return false;
    }
}

// Test connection to backend server
function checkServerHealth(url, timeoutMs = 3500) {
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
                // Try fallback index.html
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
    }, 2000);

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
    loadApplicationWithHealthCheck();

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

// Load application or show connection error
async function loadApplicationWithHealthCheck() {
    currentServerUrl = loadServerUrl();
    console.log('[Electron] Testing connection to backend:', currentServerUrl);

    const isHealthy = await checkServerHealth(currentServerUrl);
    if (isHealthy) {
        console.log('[Electron] Backend connection verified. Loading interface...');
        mainWindow.loadURL(`${currentServerUrl}/index.html`);
    } else {
        console.warn('[Electron] Backend connection failed. Displaying fallback screen...');
        mainWindow.loadFile(path.join(__dirname, 'connection-error.html'));
    }
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
                    label: 'Field Operations (Responder)',
                    accelerator: 'CmdOrCtrl+2',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/responder-dashboard.html`)
                },
                {
                    label: 'Civilian Safety Hub (Citizen)',
                    accelerator: 'CmdOrCtrl+3',
                    click: () => mainWindow && mainWindow.loadURL(`${currentServerUrl}/citizen-dashboard.html`)
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
                        loadApplicationWithHealthCheck();
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
    loadApplicationWithHealthCheck();
});

ipcMain.handle('get-app-version', () => app.getVersion());

ipcMain.handle('open-external', (event, url) => {
    if (url.startsWith('http:') || url.startsWith('https:') || url.startsWith('tel:')) {
        shell.openExternal(url);
    }
});

// App Lifecycle
app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
    }
});

