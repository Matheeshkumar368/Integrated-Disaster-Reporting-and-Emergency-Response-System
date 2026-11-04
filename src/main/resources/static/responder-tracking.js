/**
 * ==============================================================================
 * LIVE RESPONDER TRACKING & DELIVERY-APP HUD (Feature 3, 4 & 7)
 * Disaster Management & Alert System
 * 
 * Provides:
 * 1. Live Citizen/Admin Delivery-Style Tracking View (Map, Polyline, SSE, ETA).
 * 2. Responder Location Transmitter (watchPosition GPS background broadcast).
 * ==============================================================================
 */

(function () {
    'use strict';

    class ResponderTrackingViewer {
        constructor() {
            this.map = null;
            this.responderMarker = null;
            this.destMarker = null;
            this.routePolyline = null;
            this.eventSource = null;
            this.pollTimer = null;
            this.activeAssignmentId = null;

            this.injectModal();
            this.bindEvents();
        }

        injectModal() {
            if (document.getElementById('tracking-modal-backdrop')) return;

            const html = `
            <div id="tracking-modal-backdrop" class="tracking-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="tracking-title-heading">
                <div class="tracking-modal-container">
                    <div class="tracking-modal-header">
                        <div class="tracking-modal-title" id="tracking-title-heading">
                            <span>🛰️</span> LIVE RESCUE MISSION TELEMETRY
                        </div>
                        <div style="display:flex; align-items:center; gap:10px;">
                            <div id="tracking-telemetry-chip" class="tracking-status-chip">
                                <span class="pulse-dot"></span>
                                <span id="tracking-chip-text">CONNECTING</span>
                            </div>
                            <button type="button" class="media-modal-close" id="btn-tracking-close" aria-label="Close tracking view">&times;</button>
                        </div>
                    </div>

                    <div class="tracking-modal-body">
                        <!-- Leaflet Map Container -->
                        <div id="tracking-map-viewport" class="tracking-map-viewport"></div>

                        <!-- Delivery-App Floating HUD Card -->
                        <div class="tracking-hud-card">
                            <div class="hud-top-row">
                                <div class="responder-profile-info">
                                    <div class="responder-avatar-badge" id="hud-responder-avatar">
                                        🚑
                                    </div>
                                    <div>
                                        <div class="responder-name" id="hud-responder-name">Dispatching Assigned Unit...</div>
                                        <div class="responder-role-sub" id="hud-responder-role">Rapid Disaster Response Unit</div>
                                    </div>
                                </div>

                                <div class="hud-eta-box">
                                    <div class="eta-large-value" id="hud-eta-val">-- MINS</div>
                                    <div class="distance-sub-value" id="hud-dist-val">Calculating route...</div>
                                </div>
                            </div>

                            <!-- Stepper Progress Flow -->
                            <div class="hud-stepper-track">
                                <div class="hud-step-node completed" id="step-node-dispatched">
                                    <div class="step-circle"></div>
                                    <div class="step-label">Dispatched</div>
                                </div>
                                <div class="hud-step-node active" id="step-node-enroute">
                                    <div class="step-circle"></div>
                                    <div class="step-label">En Route</div>
                                </div>
                                <div class="hud-step-node" id="step-node-onscene">
                                    <div class="step-circle"></div>
                                    <div class="step-label">On Scene</div>
                                </div>
                                <div class="hud-step-node" id="step-node-completed">
                                    <div class="step-circle"></div>
                                    <div class="step-label">Resolved</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            `;

            document.body.insertAdjacentHTML('beforeend', html);
        }

        bindEvents() {
            const closeBtn = document.getElementById('btn-tracking-close');
            if (closeBtn) {
                closeBtn.addEventListener('click', () => this.close());
            }
        }

        open(assignmentId) {
            this.activeAssignmentId = assignmentId;
            const backdrop = document.getElementById('tracking-modal-backdrop');
            backdrop.classList.add('active');

            // Initialize or invalidate Map
            setTimeout(() => {
                this.initMap();
                this.fetchInitialTelemetry(assignmentId);
                this.connectRealtimeStream(assignmentId);
            }, 100);
        }

        close() {
            this.cleanupStream();
            const backdrop = document.getElementById('tracking-modal-backdrop');
            if (backdrop) backdrop.classList.remove('active');
        }

        initMap() {
            const container = document.getElementById('tracking-map-viewport');
            if (!container) return;

            if (!this.map) {
                if (typeof L === 'undefined') {
                    console.error('Leaflet is not loaded!');
                    return;
                }

                this.map = L.map('tracking-map-viewport', {
                    zoomControl: true,
                    attributionControl: false
                }).setView([13.0827, 80.2707], 13);

                // Tactical Dark Leaflet Tile Layer (Esri Dark Canvas - Zero Watermarks)
                if (window.CommandCenter && typeof window.CommandCenter.getTacticalTileLayer === 'function') {
                    window.CommandCenter.getTacticalTileLayer().addTo(this.map);
                } else {
                    L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
                        maxZoom: 16,
                        attribution: '&copy; Esri &copy; OpenStreetMap'
                    }).addTo(this.map);
                }
            } else {
                this.map.invalidateSize();
            }
        }

        async fetchInitialTelemetry(assignmentId) {
            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/tracking/latest/${assignmentId}`, {
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {}
                });
                if (res.ok) {
                    const data = await res.json();
                    this.renderTelemetry(data);
                }
            } catch (err) {
                console.warn('Could not fetch initial telemetry:', err);
            }
        }

        connectRealtimeStream(assignmentId) {
            this.cleanupStream();

            const token = localStorage.getItem('token') || '';
            const sseUrl = `/api/tracking/stream/${assignmentId}${token ? '?token=' + encodeURIComponent(token) : ''}`;

            try {
                this.eventSource = new EventSource(sseUrl);

                this.eventSource.addEventListener('tracking-update', (event) => {
                    try {
                        const data = JSON.parse(event.data);
                        this.renderTelemetry(data);
                    } catch (e) {
                        console.error('SSE payload parse error: ', e);
                    }
                });

                this.eventSource.addEventListener('open', () => {
                    this.updateChipStatus('LIVE TELEMETRY', false);
                });

                this.eventSource.onerror = () => {
                    console.warn('SSE disconnected or not supported by network proxy. Switching to polling fallback.');
                    this.eventSource.close();
                    this.eventSource = null;
                    this.startPollingFallback(assignmentId);
                };
            } catch (err) {
                this.startPollingFallback(assignmentId);
            }
        }

        startPollingFallback(assignmentId) {
            if (this.pollTimer) return;
            this.updateChipStatus('POLLING ACTIVE', false);
            this.pollTimer = setInterval(async () => {
                await this.fetchInitialTelemetry(assignmentId);
            }, 4000);
        }

        cleanupStream() {
            if (this.eventSource) {
                this.eventSource.close();
                this.eventSource = null;
            }
            if (this.pollTimer) {
                clearInterval(this.pollTimer);
                this.pollTimer = null;
            }
        }

        renderTelemetry(data) {
            if (!this.map || !data) return;

            // Update Responder Info
            if (data.responderName) {
                document.getElementById('hud-responder-name').textContent = data.responderName;
                document.getElementById('hud-responder-role').textContent = data.responderEmail || 'Emergency Unit';
            }

            // Update ETA & Distance
            const etaElem = document.getElementById('hud-eta-val');
            const distElem = document.getElementById('hud-dist-val');

            if (data.durationMinutes) {
                etaElem.textContent = `${data.durationMinutes} MINS`;
            } else if (data.etaText) {
                etaElem.textContent = data.etaText.split('(')[0].trim().toUpperCase();
            } else {
                etaElem.textContent = 'ARRIVING';
            }

            if (data.distanceKm) {
                distElem.textContent = `${data.distanceKm} km remaining (Road Network)`;
            } else if (data.distanceMeters) {
                distElem.textContent = `${Math.round(data.distanceMeters)} m remaining`;
            }

            // Update Status Stepper
            this.updateStepper(data.status);

            // Update Stale / Live Status Chip
            if (data.status === 'COMPLETED') {
                this.updateChipStatus('MISSION RESOLVED', false, 'completed');
            } else if (data.isStale) {
                this.updateChipStatus('SIGNAL STALE', true, 'stale');
            } else {
                this.updateChipStatus('LIVE TELEMETRY', false);
            }

            // Markers & Route
            const bounds = [];

            // 1. Destination Marker
            if (data.destinationLatitude && data.destinationLongitude) {
                const destLatLng = [data.destinationLatitude, data.destinationLongitude];
                bounds.push(destLatLng);

                if (!this.destMarker) {
                    const destIcon = L.divIcon({
                        className: 'destination-marker-icon',
                        html: '📍',
                        iconSize: [36, 36],
                        iconAnchor: [18, 18]
                    });
                    this.destMarker = L.marker(destLatLng, { icon: destIcon })
                        .addTo(this.map)
                        .bindPopup(`<b>Incident Target</b><br>${data.destinationAddress || 'Disaster Site'}`);
                } else {
                    this.destMarker.setLatLng(destLatLng);
                }
            }

            // 2. Responder Live Marker
            if (data.latitude && data.longitude) {
                const respLatLng = [data.latitude, data.longitude];
                bounds.push(respLatLng);

                if (!this.responderMarker) {
                    const vehicleIcon = L.divIcon({
                        className: 'vehicle-marker-icon',
                        html: `
                            <div class="vehicle-marker-pulse"></div>
                            <div class="vehicle-marker-body">🚑</div>
                        `,
                        iconSize: [40, 40],
                        iconAnchor: [20, 20]
                    });
                    this.responderMarker = L.marker(respLatLng, { icon: vehicleIcon })
                        .addTo(this.map)
                        .bindPopup(`<b>${data.responderName || 'Responder Unit'}</b><br>Speed: ${data.speed ? Math.round(data.speed * 3.6) + ' km/h' : 'Moving'}`);
                } else {
                    // Smoothly slide marker to new position
                    this.responderMarker.setLatLng(respLatLng);
                }
            }

            // 3. Polyline Route Drawing
            if (data.routeCoordinates && Array.isArray(data.routeCoordinates) && data.routeCoordinates.length > 0) {
                // GeoJSON coordinates come as [lng, lat], Leaflet polyline expects [lat, lng]
                const latLngs = data.routeCoordinates.map(pt => [pt[1], pt[0]]);
                if (!this.routePolyline) {
                    this.routePolyline = L.polyline(latLngs, {
                        color: '#00e5ff',
                        weight: 5,
                        opacity: 0.85,
                        lineCap: 'round',
                        lineJoin: 'round',
                        dashArray: '8, 8',
                        className: 'neon-route-path'
                    }).addTo(this.map);
                } else {
                    this.routePolyline.setLatLngs(latLngs);
                }
            } else if (data.latitude && data.destinationLatitude) {
                // Fallback straight line
                const straight = [
                    [data.latitude, data.longitude],
                    [data.destinationLatitude, data.destinationLongitude]
                ];
                if (!this.routePolyline) {
                    this.routePolyline = L.polyline(straight, {
                        color: '#00e5ff',
                        weight: 4,
                        opacity: 0.7,
                        dashArray: '5, 8'
                    }).addTo(this.map);
                } else {
                    this.routePolyline.setLatLngs(straight);
                }
            }

            if (bounds.length > 0) {
                this.map.fitBounds(L.latLngBounds(bounds), { padding: [60, 60], maxZoom: 16 });
            }
        }

        updateStepper(status) {
            const stepDispatched = document.getElementById('step-node-dispatched');
            const stepEnRoute = document.getElementById('step-node-enroute');
            const stepOnScene = document.getElementById('step-node-onscene');
            const stepCompleted = document.getElementById('step-node-completed');

            [stepDispatched, stepEnRoute, stepOnScene, stepCompleted].forEach(el => {
                if (el) el.className = 'hud-step-node';
            });

            if (status === 'COMPLETED' || status === 'Resolved') {
                stepDispatched.className = 'hud-step-node completed';
                stepEnRoute.className = 'hud-step-node completed';
                stepOnScene.className = 'hud-step-node completed';
                stepCompleted.className = 'hud-step-node active completed';
            } else if (status === 'ON_SCENE' || status === 'Arrived') {
                stepDispatched.className = 'hud-step-node completed';
                stepEnRoute.className = 'hud-step-node completed';
                stepOnScene.className = 'hud-step-node active';
            } else {
                stepDispatched.className = 'hud-step-node completed';
                stepEnRoute.className = 'hud-step-node active';
            }
        }

        updateChipStatus(text, isStale, customClass = '') {
            const chip = document.getElementById('tracking-telemetry-chip');
            const chipText = document.getElementById('tracking-chip-text');
            if (!chip || !chipText) return;

            chipText.textContent = text;
            chip.className = `tracking-status-chip ${customClass}`;
        }
    }

    /**
     * GPS Telemetry Transmitter for Logged-In Responders
     * Includes explicit permission consent dialog, precision telemetry display,
     * pause/resume controls, and network resilience.
     */
    class ResponderLocationTransmitter {
        constructor() {
            this.watchId = null;
            this.activeAssignmentId = null;
            this.lastSentTimestamp = 0;
            this.isTransmitting = false;
            this.isPaused = false;
            this.latestCoords = null;
            this.latestAccuracy = null;
            this.networkFailureCount = 0;
        }

        async requestPermissionAndStart(assignmentId) {
            return new Promise((resolve) => {
                let modal = document.getElementById('gps-permission-modal');
                if (!modal) {
                    modal = document.createElement('div');
                    modal.id = 'gps-permission-modal';
                    modal.style.cssText = `
                        position: fixed; top: 0; left: 0; right: 0; bottom: 0;
                        background: rgba(5, 8, 17, 0.85); backdrop-filter: blur(12px);
                        display: flex; align-items: center; justify-content: center;
                        z-index: 10000; font-family: inherit;
                    `;
                    document.body.appendChild(modal);
                }

                modal.innerHTML = `
                    <div style="background: #091322; border: 1px solid #00e5ff; border-radius: 12px; padding: 24px; max-width: 480px; width: 90%; box-shadow: 0 10px 40px rgba(0,229,255,0.25); color: #fff;">
                        <div style="display:flex; align-items:center; gap:12px; margin-bottom:14px;">
                            <span style="font-size:1.8rem; color:#00e5ff;">📡</span>
                            <h3 style="margin:0; font-size:1.2rem; color:#fff;">Start Tactical GPS Location Sharing</h3>
                        </div>
                        <p style="color:#cbd5e1; font-size:0.88rem; line-height:1.5; margin-bottom:14px;">
                            <strong>Purpose:</strong> Real-time situational awareness for Central Command and civilian safety.
                            Your coordinates will be transmitted securely to generate live road route navigation and ETA.
                        </p>
                        <div style="background:rgba(255,255,255,0.04); border-left:3px solid #00e5ff; padding:10px 14px; border-radius:4px; font-size:0.8rem; color:#94a3b8; margin-bottom:20px;">
                            <i class="fas fa-shield-halved" style="color:#00e5ff;"></i> <strong>Privacy Note:</strong> Telemetry is broadcast only while this specific mission is in active status and terminates automatically upon completion.
                        </div>
                        <div style="display:flex; gap:10px; justify-content:flex-end;">
                            <button type="button" id="btn-gps-cancel" style="background:transparent; border:1px solid #475569; color:#cbd5e1; padding:8px 16px; border-radius:6px; cursor:pointer; font-size:0.85rem;">Cancel</button>
                            <button type="button" id="btn-gps-confirm" style="background:linear-gradient(135deg, #0284c7, #00e5ff); border:none; color:#030a16; font-weight:700; padding:8px 18px; border-radius:6px; cursor:pointer; font-size:0.85rem;">
                                Allow & Start Sharing
                            </button>
                        </div>
                    </div>
                `;

                modal.style.display = 'flex';

                document.getElementById('btn-gps-cancel').onclick = () => {
                    modal.style.display = 'none';
                    resolve(false);
                };

                document.getElementById('btn-gps-confirm').onclick = async () => {
                    modal.style.display = 'none';
                    await this.start(assignmentId);
                    resolve(true);
                };
            });
        }

        async start(assignmentId) {
            if (this.isTransmitting && this.activeAssignmentId === assignmentId) return;

            this.activeAssignmentId = assignmentId;
            this.isTransmitting = true;
            this.isPaused = false;
            this.networkFailureCount = 0;

            if (!navigator.geolocation) {
                if (window.showToast) window.showToast('Geolocation is not supported by your browser.', 'error');
                else if (window.CommandCenter) CommandCenter.showToast('Geolocation is not supported by your browser.', 'error');
                this.isTransmitting = false;
                return;
            }

            // Send initial START request
            navigator.geolocation.getCurrentPosition(async (pos) => {
                this.latestCoords = `${pos.coords.latitude.toFixed(5)}° N, ${pos.coords.longitude.toFixed(5)}° E`;
                this.latestAccuracy = Math.round(pos.coords.accuracy);

                await this.sendTelemetry('/api/responder/location/start', {
                    taskId: assignmentId,
                    assignmentId: assignmentId,
                    latitude: pos.coords.latitude,
                    longitude: pos.coords.longitude,
                    accuracy: pos.coords.accuracy
                });

                this.showTransmitterBanner(assignmentId);
            }, (err) => {
                console.warn('Initial GPS lookup failed:', err.message);
                this.showTransmitterBanner(assignmentId, err.message);
            }, { enableHighAccuracy: true });

            // Begin continuous watch
            this.watchId = navigator.geolocation.watchPosition(
                async (pos) => {
                    if (this.isPaused) return;

                    const now = Date.now();
                    // Throttle updates to once every 4 seconds
                    if (now - this.lastSentTimestamp < 4000) return;
                    this.lastSentTimestamp = now;

                    this.latestCoords = `${pos.coords.latitude.toFixed(5)}° N, ${pos.coords.longitude.toFixed(5)}° E`;
                    this.latestAccuracy = Math.round(pos.coords.accuracy);

                    const success = await this.sendTelemetry('/api/responder/location/update', {
                        taskId: assignmentId,
                        assignmentId: assignmentId,
                        latitude: pos.coords.latitude,
                        longitude: pos.coords.longitude,
                        accuracy: pos.coords.accuracy,
                        heading: pos.coords.heading || 0,
                        speed: pos.coords.speed || 0
                    });

                    if (success) {
                        this.networkFailureCount = 0;
                    } else {
                        this.networkFailureCount++;
                    }

                    this.updateTransmitterBanner(assignmentId);
                },
                (err) => {
                    console.warn('Location transmitter error: ', err.message);
                    this.updateTransmitterBanner(assignmentId, err.message);
                },
                { enableHighAccuracy: true, maximumAge: 3000, timeout: 10000 }
            );
        }

        pause() {
            this.isPaused = true;
            this.updateTransmitterBanner(this.activeAssignmentId);
            if (window.CommandCenter) CommandCenter.showToast('GPS telemetry paused', 'warning');
        }

        resume() {
            this.isPaused = false;
            this.updateTransmitterBanner(this.activeAssignmentId);
            if (window.CommandCenter) CommandCenter.showToast('GPS telemetry resumed', 'success');
        }

        async stop() {
            if (!this.isTransmitting) return;

            if (this.watchId !== null) {
                navigator.geolocation.clearWatch(this.watchId);
                this.watchId = null;
            }

            if (this.activeAssignmentId) {
                await this.sendTelemetry('/api/responder/location/stop', { taskId: this.activeAssignmentId, assignmentId: this.activeAssignmentId });
            }

            this.isTransmitting = false;
            this.isPaused = false;
            this.activeAssignmentId = null;
            this.hideTransmitterBanner();
            if (window.CommandCenter) CommandCenter.showToast('GPS telemetry session concluded', 'info');
        }

        async sendTelemetry(url, payload) {
            const token = localStorage.getItem('token');
            try {
                const res = await fetch(url, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
                    },
                    body: JSON.stringify(payload)
                });
                return res.ok;
            } catch (err) {
                console.warn('Telemetry packet failed to transmit:', err);
                return false;
            }
        }

        showTransmitterBanner(assignmentId, errorMsg) {
            let banner = document.getElementById('gps-transmitter-banner');
            if (!banner) {
                banner = document.createElement('div');
                banner.id = 'gps-transmitter-banner';
                banner.style.cssText = `
                    position: fixed;
                    bottom: 20px;
                    right: 20px;
                    background: linear-gradient(135deg, #091e3a, #040d1a);
                    border: 1px solid #00e5ff;
                    border-radius: 12px;
                    padding: 12px 18px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.8), 0 0 20px rgba(0,229,255,0.3);
                    z-index: 9999;
                    display: flex;
                    align-items: center;
                    gap: 14px;
                    color: #fff;
                    font-family: inherit;
                    font-size: 0.85rem;
                    max-width: 95vw;
                `;
                document.body.appendChild(banner);
            }
            this.updateTransmitterBanner(assignmentId, errorMsg);
        }

        updateTransmitterBanner(assignmentId, errorMsg) {
            const banner = document.getElementById('gps-transmitter-banner');
            if (!banner) return;

            const isPaused = this.isPaused;
            const statusDot = isPaused ? '#f59e0b' : '#10b981';
            const statusText = isPaused ? 'GPS PAUSED' : 'LIVE GPS BROADCASTING';

            let coordsText = this.latestCoords ? 
                `Loc: ${this.latestCoords} (&plusmn;${this.latestAccuracy || 5}m)` : 
                'Acquiring GPS fix...';

            if (errorMsg) {
                coordsText = `<span style="color:#ef4444;">${errorMsg}</span>`;
            } else if (this.networkFailureCount > 0) {
                coordsText = `<span style="color:#f59e0b;">Network degraded (${this.networkFailureCount} retries)</span>`;
            }

            banner.innerHTML = `
                <span class="pulse-dot" style="background:${statusDot};box-shadow:0 0 8px ${statusDot};"></span>
                <div style="flex:1;">
                    <div style="font-weight:700;display:flex;align-items:center;gap:6px;">
                        <span>${statusText}</span>
                        <span style="font-size:0.75rem;color:var(--cc-cyan);font-family:var(--cc-font-mono);">#TASK-${assignmentId}</span>
                    </div>
                    <div style="font-size:0.74rem;color:#94a3b8;font-family:var(--cc-font-mono);margin-top:2px;">
                        ${coordsText}
                    </div>
                </div>
                <div style="display:flex;gap:6px;">
                    <button type="button" id="btn-toggle-pause-gps" style="
                        background: rgba(255,255,255,0.08);
                        border: 1px solid #475569;
                        color: #cbd5e1;
                        padding: 5px 9px;
                        border-radius: 6px;
                        font-size: 0.75rem;
                        cursor: pointer;
                        font-weight: 600;
                    ">${isPaused ? 'Resume' : 'Pause'}</button>
                    <button type="button" id="btn-stop-gps-transmitting" style="
                        background: rgba(239,68,68,0.2);
                        border: 1px solid #ef4444;
                        color: #f87171;
                        padding: 5px 10px;
                        border-radius: 6px;
                        font-size: 0.75rem;
                        cursor: pointer;
                        font-weight: 700;
                    ">Stop</button>
                </div>
            `;

            document.getElementById('btn-toggle-pause-gps').onclick = () => {
                if (this.isPaused) this.resume();
                else this.pause();
            };

            document.getElementById('btn-stop-gps-transmitting').onclick = () => this.stop();
        }

        hideTransmitterBanner() {
            const banner = document.getElementById('gps-transmitter-banner');
            if (banner) banner.remove();
        }
    }

    // Export singletons
    window.ResponderTracking = new ResponderTrackingViewer();
    window.ResponderTransmitter = new ResponderLocationTransmitter();
})();

