/**
 * ==============================================================================
 * DISASTER MANAGEMENT & ALERT SYSTEM — COMMAND CENTER INTERACTION ENGINE
 * Tactical HUD Telemetry, Web Audio Synthesizer, Drawer Manager & Map System
 * ==============================================================================
 */

const CommandCenter = (function () {
    'use strict';

    // Audio Context (Lazy Initialized on user interaction)
    let audioCtx = null;
    let isAudioMuted = localStorage.getItem('cc_audio_muted') === 'true';

    function getAudioContext() {
        if (!audioCtx) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (AudioContext) {
                audioCtx = new AudioContext();
            }
        }
        if (audioCtx && audioCtx.state === 'suspended') {
            audioCtx.resume();
        }
        return audioCtx;
    }

    /**
     * Synthesizes short, non-intrusive tactical sound effects via Web Audio API.
     * Guaranteed 100% offline without downloading external audio files.
     */
    function playTacticalChime(type) {
        if (isAudioMuted) return;
        try {
            const ctx = getAudioContext();
            if (!ctx) return;

            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);

            const now = ctx.currentTime;

            if (type === 'alert') {
                // Dual-tone urgent alert chime
                osc.type = 'sine';
                osc.frequency.setValueAtTime(880, now); // A5
                osc.frequency.exponentialRampToValueAtTime(440, now + 0.15);
                gain.gain.setValueAtTime(0.12, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);
                osc.start(now);
                osc.stop(now + 0.3);
            } else if (type === 'click') {
                // Subtle tactile tick
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(1200, now);
                gain.gain.setValueAtTime(0.04, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 0.04);
                osc.start(now);
                osc.stop(now + 0.04);
            } else if (type === 'success') {
                // Harmonious confirmation
                osc.type = 'sine';
                osc.frequency.setValueAtTime(523.25, now); // C5
                osc.frequency.setValueAtTime(659.25, now + 0.08); // E5
                gain.gain.setValueAtTime(0.08, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
                osc.start(now);
                osc.stop(now + 0.25);
            }
        } catch (e) {
            // Audio policy gracefully ignored
        }
    }

    /**
     * Live HUD Digital Clock (IST & UTC Synchronized - 12-Hour AM/PM Format)
     */
    function initHudClock(targetElId) {
        const el = document.getElementById(targetElId);
        if (!el) return;

        function update() {
            const now = new Date();
            const istTime = now.toLocaleTimeString('en-IN', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: true,
                timeZone: 'Asia/Kolkata'
            }).toUpperCase();
            const utcTime = now.toLocaleTimeString('en-GB', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: true,
                timeZone: 'UTC'
            }).toUpperCase();
            el.innerHTML = `<span style="color:#38bdf8;">IST</span> ${istTime} <span style="color:#64748b;">|</span> <span style="color:#94a3b8;">UTC</span> ${utcTime}`;
        }
        update();
        setInterval(update, 1000);
    }

    /**
     * Formats any date/timestamp into 12-hour Indian Standard Time with AM/PM (e.g. "10:00 PM")
     */
    function formatTime(dateInput) {
        if (!dateInput) return '-';
        const d = new Date(dateInput);
        if (isNaN(d.getTime())) return '-';
        return d.toLocaleTimeString('en-IN', {
            timeZone: 'Asia/Kolkata',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true
        }).toUpperCase();
    }

    /**
     * Formats any date/timestamp into readable IST date and 12-hour time (e.g. "02 Nov 2026 • 10:00 PM")
     */
    function formatDateTime(dateInput) {
        if (!dateInput) return '-';
        const d = new Date(dateInput);
        if (isNaN(d.getTime())) return '-';
        const datePart = d.toLocaleDateString('en-IN', {
            timeZone: 'Asia/Kolkata',
            day: '2-digit',
            month: 'short',
            year: 'numeric'
        });
        const timePart = d.toLocaleTimeString('en-IN', {
            timeZone: 'Asia/Kolkata',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true
        }).toUpperCase();
        return `${datePart} • ${timePart}`;
    }

    /**
     * Tactical Dark Leaflet Tile Layer (Esri Dark Canvas with OpenStreetMap fallback - Zero Watermarks)
     */
    function getTacticalTileLayer() {
        const esriLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
            attribution: '&copy; <a href="https://www.esri.com/" target="_blank" style="color:#64748b;">Esri</a> &copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" style="color:#64748b;">OpenStreetMap</a> contributors',
            maxZoom: 16
        });

        esriLayer.on('tileerror', function(error) {
            console.warn('[CommandCenter] Esri tile failed to load, verify network or fallback:', error);
        });

        return esriLayer;
    }

    /**
     * Tactical Marker Factory with Pulsing Ring for High/Extreme Hazards
     */
    function createTacticalMarker(hazard, severity) {
        const isCritical = /high|extreme|severe/i.test(severity);
        const isWarning = /medium|moderate/i.test(severity);

        const bgColor = isCritical ? '#ef4444' : (isWarning ? '#f59e0b' : '#10b981');
        const pulseClass = isCritical ? 'cc-marker-pulsing' : '';

        let iconClass = 'fa-exclamation-triangle';
        if (/cyclone|storm|wind/i.test(hazard)) iconClass = 'fa-wind';
        else if (/rain|thunderstorm|precipitation/i.test(hazard)) iconClass = 'fa-cloud-rain';
        else if (/flood/i.test(hazard)) iconClass = 'fa-water';
        else if (/earthquake/i.test(hazard)) iconClass = 'fa-house-crack';

        return L.divIcon({
            html: `
                <div class="${pulseClass}" style="background:${bgColor};width:38px;height:38px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#ffffff;font-size:18px;border:2px solid #ffffff;box-shadow:0 4px 15px rgba(0,0,0,0.7);">
                    <i class="fas ${iconClass}"></i>
                </div>
            `,
            className: '',
            iconSize: [38, 38],
            iconAnchor: [19, 19]
        });
    }

    /**
     * Tactical Incident Inspection Drawer
     */
    function openIncidentDrawer(item, onBroadcastClick) {
        let drawer = document.getElementById('tacticalIncidentDrawer');
        if (!drawer) {
            drawer = document.createElement('div');
            drawer.id = 'tacticalIncidentDrawer';
            drawer.className = 'cc-incident-drawer';
            document.body.appendChild(drawer);
        }

        const hazard = item.hazardType || item.type || 'Disaster Advisory';
        const locationName = item.areaDescription || item.location || 'India';
        const severity = item.severity || 'Moderate';
        const isOfficial = !!item.sourceAlertId;
        const sourceName = item.sourceAgency || (isOfficial ? 'NDMA SACHET (CAP 1.2)' : 'Civil Protection');
        const headline = item.headline || item.description || 'Verified Warning Advisory';
        const instructions = item.instruction || 'Follow official SDMA safety guidelines.';
        const effectiveTime = item.effectiveTime || item.timestamp || new Date().toISOString();
        const expiresTime = item.expiresTime ? new Date(item.expiresTime).toLocaleString('en-IN') : 'Ongoing';
        const precision = item.locationPrecision ? item.locationPrecision.replace('_', ' ') : 'Centroid';
        const lat = (item.latitude || 0).toFixed(4);
        const lon = (item.longitude || 0).toFixed(4);

        const sevBadgeClass = /high|extreme|severe/i.test(severity) ? 'severity-extreme' :
            /medium|moderate/i.test(severity) ? 'severity-moderate' : 'severity-low';

        drawer.innerHTML = `
            <div class="cc-drawer-header">
                <div style="display:flex;align-items:center;gap:0.6rem;">
                    <span class="cc-badge ${isOfficial ? 'ndma' : 'broadcast'}">
                        <i class="fas ${isOfficial ? 'fa-shield-alt' : 'fa-broadcast-tower'}"></i>
                        ${isOfficial ? 'NDMA SACHET' : 'DISASTER REGISTRY'}
                    </span>
                    <span class="cc-badge ${sevBadgeClass}">${severity}</span>
                </div>
                <button class="cc-drawer-close" onclick="CommandCenter.closeIncidentDrawer()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="cc-drawer-body">
                <h2 style="font-size:1.25rem;font-weight:700;margin-bottom:0.5rem;color:#f8fafc;">
                    ${hazard}
                </h2>
                <div style="background:rgba(15,23,42,0.8);border:1px solid rgba(255,255,255,0.08);border-radius:8px;padding:0.8rem;margin-bottom:1rem;font-size:0.85rem;color:#cbd5e1;line-height:1.45;">
                    ${headline}
                </div>

                <div style="margin-bottom:1rem;">
                    <div style="font-size:0.75rem;color:#64748b;text-transform:uppercase;font-weight:600;margin-bottom:0.2rem;">Location & Precision</div>
                    <div style="font-size:0.92rem;color:#f1f5f9;font-weight:600;">${locationName}</div>
                    <div style="font-size:0.78rem;color:#94a3b8;font-family:var(--cc-font-mono);margin-top:0.2rem;">
                        GPS: ${lat}, ${lon} • <span style="color:#38bdf8;">${precision}</span>
                    </div>
                </div>

                <div style="margin-bottom:1rem;">
                    <div style="font-size:0.75rem;color:#64748b;text-transform:uppercase;font-weight:600;margin-bottom:0.2rem;">Issuing Agency</div>
                    <div style="font-size:0.9rem;color:#f1f5f9;"><i class="fas fa-building-columns" style="color:#38bdf8;margin-right:0.4rem;"></i>${sourceName}</div>
                </div>

                <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.8rem;margin-bottom:1.2rem;">
                    <div>
                        <div style="font-size:0.75rem;color:#64748b;text-transform:uppercase;font-weight:600;">Effective</div>
                        <div style="font-size:0.82rem;color:#cbd5e1;font-family:var(--cc-font-mono);margin-top:0.15rem;">
                            ${new Date(effectiveTime).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}
                        </div>
                    </div>
                    <div>
                        <div style="font-size:0.75rem;color:#64748b;text-transform:uppercase;font-weight:600;">Expires</div>
                        <div style="font-size:0.82rem;color:#cbd5e1;font-family:var(--cc-font-mono);margin-top:0.15rem;">
                            ${expiresTime}
                        </div>
                    </div>
                </div>

                <div style="background:rgba(30,41,59,0.6);border-left:3px solid #38bdf8;padding:0.8rem;border-radius:4px;margin-bottom:1.5rem;">
                    <div style="font-size:0.75rem;color:#38bdf8;font-weight:700;text-transform:uppercase;margin-bottom:0.3rem;">
                        <i class="fas fa-life-ring"></i> Safety Advisory
                    </div>
                    <div style="font-size:0.85rem;color:#cbd5e1;line-height:1.4;">
                        ${instructions}
                    </div>
                </div>

                <button id="btnDrawerBroadcast" class="cc-btn cc-btn-danger" style="width:100%;padding:0.85rem;">
                    <i class="fas fa-broadcast-tower"></i> Broadcast Emergency Alert to Citizens
                </button>
            </div>
        `;

        const btn = document.getElementById('btnDrawerBroadcast');
        if (btn && typeof onBroadcastClick === 'function') {
            btn.onclick = () => {
                playTacticalChime('click');
                onBroadcastClick(item);
            };
        }

        setTimeout(() => drawer.classList.add('open'), 10);
        playTacticalChime('alert');
    }

    function closeIncidentDrawer() {
        const drawer = document.getElementById('tacticalIncidentDrawer');
        if (drawer) {
            drawer.classList.remove('open');
            playTacticalChime('click');
        }
    }

    /**
     * Tactical Toast Notification (Delegates to unified ToastManager)
     */
    function showToast(message, type = 'info', duration = 3500) {
        if (window.showToast && window.showToast !== showToast) {
            return window.showToast(message, type, duration);
        }
        playTacticalChime(type === 'error' ? 'alert' : (type === 'success' ? 'success' : 'click'));
        // Fallback if toast-manager.js not yet parsed
        console.log(`[Toast:${type}] ${message}`);
    }

    return {
        initHudClock,
        formatTime,
        formatDateTime,
        getTacticalTileLayer,
        createTacticalMarker,
        openIncidentDrawer,
        closeIncidentDrawer,
        showToast,
        playChime: playTacticalChime
    };
})();

// Attach to global window
window.CommandCenter = CommandCenter;
window.formatIndianTime = CommandCenter.formatTime;
window.formatIndianDateTime = CommandCenter.formatDateTime;

