/**
 * NDMA Tactical Command Center - Progressive Web App Service Worker
 * Version: 1.0.0
 * Security Rules:
 * - Pre-cache only safe static application assets & offline shell.
 * - NEVER cache sensitive API responses (/api/**), tokens, or authenticated personal data.
 * - Always deliver real-time network-first disaster alerts.
 */

const CACHE_NAME = 'ndma-command-shell-v1';

const STATIC_SHELL_ASSETS = [
    '/',
    '/index.html',
    '/offline.html',
    '/command-center.css?v=3',
    '/command-center.js?v=3',
    '/auth-helper.js?v=2',
    '/pwa-manager.js',
    '/manifest.webmanifest',
    '/manifest.json',
    '/icons/icon.svg',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/favicon.ico',
    'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@500;600;700&display=swap',
    'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css'
];

// 1. Install Event: Cache safe static assets
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            console.log('[ServiceWorker] Pre-caching offline emergency shell');
            return cache.addAll(STATIC_SHELL_ASSETS).catch((err) => {
                console.warn('[ServiceWorker] Some assets could not be pre-cached:', err);
            });
        }).then(() => self.skipWaiting())
    );
});

// 2. Activate Event: Clean up outdated cache generations
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames.map((name) => {
                    if (name !== CACHE_NAME) {
                        console.log('[ServiceWorker] Purging legacy cache generation:', name);
                        return caches.delete(name);
                    }
                })
            );
        }).then(() => self.clients.claim())
    );
});

// 3. Fetch Event: Safe Network-First & Offline Fail-Safe Strategy
self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);

    // CRITICAL SECURITY RULE:
    // Any API request (/api/**) MUST NEVER be saved to shared offline cache.
    // Real emergency alerts and citizen reports must never be served as fake live data.
    if (url.pathname.startsWith('/api/')) {
        event.respondWith(
            fetch(request).catch(() => {
                return new Response(
                    JSON.stringify({
                        offline: true,
                        status: 'offline',
                        message: 'Network offline. Live government disaster telemetry and authenticated actions require an active connection.'
                    }),
                    {
                        status: 503,
                        statusText: 'Service Unavailable (Offline)',
                        headers: { 'Content-Type': 'application/json' }
                    }
                );
            })
        );
        return;
    }

    // HTML Navigation requests: Network-first, fallback to cache, then offline.html
    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request).then((networkResponse) => {
                // If valid response, update cache copy
                if (networkResponse && networkResponse.status === 200) {
                    const responseClone = networkResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(request, responseClone));
                }
                return networkResponse;
            }).catch(async () => {
                const cachedResponse = await caches.match(request);
                if (cachedResponse) return cachedResponse;
                const offlineFallback = await caches.match('/offline.html');
                return offlineFallback || new Response('Offline - No connection available', { status: 503 });
            })
        );
        return;
    }

    // Static assets (CSS, JS, Fonts, Images): Stale-While-Revalidate
    event.respondWith(
        caches.match(request).then((cachedResponse) => {
            const fetchPromise = fetch(request).then((networkResponse) => {
                if (networkResponse && networkResponse.status === 200 && request.method === 'GET') {
                    const responseClone = networkResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(request, responseClone));
                }
                return networkResponse;
            }).catch(() => {
                // Return cached if network fails
                return cachedResponse;
            });

            return cachedResponse || fetchPromise;
        })
    );
});

// 4. Web Push Notifications (where supported and authorized)
self.addEventListener('push', (event) => {
    let payload = {
        title: 'NDMA Disaster Alert',
        body: 'New official emergency warning received in your jurisdiction.',
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        url: '/citizen-dashboard.html?tab=alerts'
    };

    if (event.data) {
        try {
            payload = Object.assign(payload, event.data.json());
        } catch (e) {
            payload.body = event.data.text();
        }
    }

    const options = {
        body: payload.body,
        icon: payload.icon || '/icons/icon-192.png',
        badge: payload.badge || '/icons/icon-192.png',
        vibrate: [250, 100, 250, 100, 250],
        tag: 'ndma-emergency-alert',
        renotify: true,
        data: {
            url: payload.url || '/citizen-dashboard.html'
        },
        actions: [
            { action: 'view', title: 'Inspect Alert' },
            { action: 'dismiss', title: 'Dismiss' }
        ]
    };

    event.waitUntil(
        self.registration.showNotification(payload.title, options)
    );
});

// 5. Notification Click Handler
self.addEventListener('notificationclick', (event) => {
    event.notification.close();

    if (event.action === 'dismiss') return;

    const targetUrl = (event.notification.data && event.notification.data.url) || '/';

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
            for (let client of windowClients) {
                if (client.url.includes(targetUrl) && 'focus' in client) {
                    return client.focus();
                }
            }
            if (clients.openWindow) {
                return clients.openWindow(targetUrl);
            }
        })
    );
});

