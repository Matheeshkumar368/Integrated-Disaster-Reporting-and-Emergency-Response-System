/**
 * NDMA Tactical Command Center - PWA Manager
 * Handles Service Worker registration, Install Prompts, Online/Offline Telemetry,
 * and Contextual Notification Permission.
 */

const PwaManager = (function() {
    let deferredPrompt = null;
    let isOffline = !navigator.onLine;

    // Initialize on DOM load
    function init() {
        registerServiceWorker();
        setupOnlineOfflineListeners();
        setupInstallPrompt();
        injectOfflineBanner();
        updateConnectivityState();
    }

    // 1. Service Worker Registration
    async function registerServiceWorker() {
        if ('serviceWorker' in navigator) {
            try {
                const reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
                console.log('[PWA] ServiceWorker registered with scope:', reg.scope);

                // Check for updates
                reg.addEventListener('updatefound', () => {
                    const newWorker = reg.installing;
                    if (newWorker) {
                        newWorker.addEventListener('statechange', () => {
                            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                                showUpdateAvailableBanner();
                            }
                        });
                    }
                });
            } catch (err) {
                console.warn('[PWA] ServiceWorker registration failed:', err);
            }
        }
    }

    // 2. Connectivity Monitoring
    function setupOnlineOfflineListeners() {
        window.addEventListener('online', () => {
            isOffline = false;
            updateConnectivityState();
            if (window.CommandCenterSound) CommandCenterSound.playSuccess();
        });

        window.addEventListener('offline', () => {
            isOffline = true;
            updateConnectivityState();
            if (window.CommandCenterSound) CommandCenterSound.playAlert();
        });
    }

    function injectOfflineBanner() {
        if (document.getElementById('pwaConnectivityBanner')) return;

        const banner = document.createElement('div');
        banner.id = 'pwaConnectivityBanner';
        banner.style.cssText = `
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            z-index: 99999;
            background: linear-gradient(135deg, rgba(245, 158, 11, 0.95), rgba(217, 119, 6, 0.95));
            color: #ffffff;
            padding: 0.5rem 1rem;
            font-size: 0.8rem;
            font-weight: 600;
            letter-spacing: 0.05em;
            text-align: center;
            box-shadow: 0 4px 15px rgba(0,0,0,0.5);
            font-family: var(--cc-font-mono, monospace);
            backdrop-filter: blur(8px);
        `;
        banner.innerHTML = `
            <div style="display:flex; align-items:center; justify-content:center; gap:0.6rem; flex-wrap:wrap;">
                <i class="fas fa-triangle-exclamation"></i>
                <span id="connectivityStatusText">OFFLINE MODE // LIVE TELEMETRY PAUSED — CELLULAR HOTLINES ACTIVE (112 / 1070)</span>
                <button type="button" onclick="window.location.reload()" style="background:rgba(0,0,0,0.3); border:1px solid rgba(255,255,255,0.4); color:white; padding:0.2rem 0.6rem; border-radius:4px; font-size:0.75rem; cursor:pointer;">Retry</button>
            </div>
        `;
        document.body.appendChild(banner);
    }

    function updateConnectivityState() {
        const banner = document.getElementById('pwaConnectivityBanner');
        const text = document.getElementById('connectivityStatusText');
        if (!banner) return;

        if (isOffline) {
            banner.style.display = 'block';
            banner.style.background = 'linear-gradient(135deg, rgba(239, 68, 68, 0.95), rgba(185, 28, 28, 0.95))';
            if (text) text.innerHTML = '<i class="fas fa-satellite-dish"></i> OFFLINE MODE // SATELLITE DISCONNECTED — ACTIONS QUEUED FOR RECONNECT';
        } else {
            // Briefly show restored connection, then hide
            if (banner.style.display === 'block') {
                banner.style.background = 'linear-gradient(135deg, rgba(16, 185, 129, 0.95), rgba(5, 150, 105, 0.95))';
                if (text) text.innerHTML = '<i class="fas fa-circle-check"></i> CONNECTION RESTORED // SYNCHRONIZING REAL-TIME TELEMETRY...';
                setTimeout(() => {
                    banner.style.display = 'none';
                }, 3000);
            }
        }
    }

    function showUpdateAvailableBanner() {
        const banner = document.getElementById('pwaConnectivityBanner');
        if (banner) {
            banner.style.display = 'block';
            banner.style.background = 'linear-gradient(135deg, #0ea5e9, #2563eb)';
            banner.innerHTML = `
                <div style="display:flex; align-items:center; justify-content:center; gap:0.6rem;">
                    <i class="fas fa-arrows-rotate"></i>
                    <span>NEW COMMAND CENTER UPDATE AVAILABLE</span>
                    <button type="button" onclick="window.location.reload()" style="background:white; color:#0f172a; border:none; padding:0.2rem 0.6rem; border-radius:4px; font-weight:700; cursor:pointer;">Update Now</button>
                </div>
            `;
        }
    }

    // 3. Install Prompting
    function setupInstallPrompt() {
        window.addEventListener('beforeinstallprompt', (e) => {
            e.preventDefault();
            deferredPrompt = e;
            showInstallButton();
        });

        window.addEventListener('appinstalled', () => {
            console.log('[PWA] Application successfully installed as standalone app.');
            deferredPrompt = null;
            hideInstallButton();
            if (window.CommandCenterToast) {
                CommandCenterToast.show('NDMA Command App successfully installed on device.', 'success');
            }
        });

        // Detect iOS Safari standalone installation instructions
        const isIos = /iphone|ipad|ipod/.test(navigator.userAgent.toLowerCase());
        const isStandalone = window.navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;
        if (isIos && !isStandalone) {
            // iOS Safari detected without standalone mode
            injectIosInstallHelp();
        }
    }

    function showInstallButton() {
        let btn = document.getElementById('pwaInstallBtn');
        if (!btn) {
            btn = document.createElement('button');
            btn.id = 'pwaInstallBtn';
            btn.className = 'cc-btn cc-btn-primary';
            btn.style.cssText = `
                display: inline-flex;
                align-items: center;
                gap: 0.4rem;
                padding: 0.35rem 0.75rem;
                font-size: 0.75rem;
                font-family: var(--cc-font-mono, monospace);
                cursor: pointer;
            `;
            btn.innerHTML = '<i class="fas fa-download"></i> Install App';
            btn.onclick = promptInstall;

            // Try to append into header right controls or bottom toolbar
            const headerRight = document.querySelector('.cc-header-right') || document.querySelector('.gateway-top-telemetry');
            if (headerRight) {
                headerRight.prepend(btn);
            }
        }
        btn.style.display = 'inline-flex';
    }

    function hideInstallButton() {
        const btn = document.getElementById('pwaInstallBtn');
        if (btn) btn.style.display = 'none';
    }

    async function promptInstall() {
        if (!deferredPrompt) {
            if (window.showToast) {
                window.showToast('To install NDMA Command: Click browser menu (⋮ or ⋯) and select "Install App" or "Add to Home Screen".', 'info', 5000);
            } else {
                window.showToast('To install NDMA Command: Click browser menu (⋮ or ⋯) and select "Install App" or "Add to Home Screen".', 'info', 5000);
            }
            return;
        }

        deferredPrompt.prompt();
        const { outcome } = await deferredPrompt.userChoice;
        console.log('[PWA] Install prompt outcome:', outcome);
        deferredPrompt = null;
        hideInstallButton();
    }

    function injectIosInstallHelp() {
        // Can be triggered from user action in footer
        window.showIosInstallHelp = function() {
            if (window.showToast) {
                window.showToast('To install on iOS: Tap Share (square with arrow) -> Add to Home Screen -> Add.', 'info', 5000);
            } else {
                window.showToast('To install on iOS: Tap Share -> Add to Home Screen -> Add.', 'info', 5000);
            }
        };
    }

    // 4. Contextual Notification Permission
    async function requestNotificationPermission() {
        if (!('Notification' in window)) {
            if (window.showToast) {
                window.showToast('Browser Push Notifications are not supported by this browser.', 'warning');
            } else {
                window.showToast('Browser Push Notifications are not supported by this browser.', 'warning');
            }
            return false;
        }

        if (Notification.permission === 'granted') {
            if (window.showToast) {
                window.showToast('✓ Emergency notifications are active.', 'success');
            } else if (window.CommandCenterToast) {
                CommandCenterToast.show('Emergency broadcast notifications already enabled.', 'success');
            } else {
                window.showToast('✓ Emergency notifications are active.', 'success');
            }
            return true;
        }

        if (Notification.permission === 'denied') {
            if (window.showToast) {
                window.showToast('Emergency notifications are blocked in browser settings. Please enable notifications.', 'warning');
            } else {
                window.showToast('Emergency notifications are blocked in browser settings. Please enable notifications.', 'warning');
            }
            return false;
        }

        const permission = await Notification.requestPermission();
        if (permission === 'granted') {
            if (window.CommandCenterToast) {
                CommandCenterToast.show('✓ Emergency disaster alerts enabled on this device.', 'success');
            }
            // Trigger sample test notification
            if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
                navigator.serviceWorker.controller.postMessage({ type: 'TEST_NOTIFICATION' });
            }
            return true;
        }
        return false;
    }

    // 5. Freshness calculation
    function formatFreshness(timestamp) {
        if (!timestamp) return 'Time unknown';
        const now = Date.now();
        const past = new Date(timestamp).getTime();
        const diffMinutes = Math.floor((now - past) / 60000);

        if (diffMinutes < 2) return 'Just now (Live)';
        if (diffMinutes < 60) return `${diffMinutes}m ago (Active)`;
        const diffHours = Math.floor(diffMinutes / 60);
        if (diffHours < 24) return `${diffHours}h ago`;
        return `${Math.floor(diffHours / 24)}d ago`;
    }

    // Public API
    return {
        init: init,
        promptInstall: promptInstall,
        requestNotificationPermission: requestNotificationPermission,
        formatFreshness: formatFreshness,
        isOffline: function() { return isOffline; }
    };
})();

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', PwaManager.init);
} else {
    PwaManager.init();
}

