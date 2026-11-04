/**
 * NDMA Tactical Command Center - Electron Preload Script
 * Security: Context Isolation Enabled, Node Integration Disabled, Sandboxed.
 */

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopAPI', {
    isDesktop: true,
    platform: process.platform,
    
    // Server Configuration
    getServerUrl: () => ipcRenderer.invoke('get-server-url'),
    setServerUrl: (url) => ipcRenderer.invoke('set-server-url', url),
    testConnection: (url) => ipcRenderer.invoke('test-connection', url),
    
    // App Metadata
    getAppVersion: () => ipcRenderer.invoke('get-app-version'),

    // Reconnect / Navigation
    reconnect: () => ipcRenderer.invoke('reconnect-server'),
    openServerConfig: () => ipcRenderer.invoke('open-server-config'),
    
    // External Links (Hotlines, Maps)
    openExternal: (url) => ipcRenderer.invoke('open-external', url)
});

