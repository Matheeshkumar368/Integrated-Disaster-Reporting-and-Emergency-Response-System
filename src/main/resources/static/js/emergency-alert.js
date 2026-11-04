/**
 * Custom Emergency SOS Sound & Keyboard Shortcut System
 * 
 * Provides an offline, Web Audio API-generated emergency SOS Morse sound (... --- ...)
 * with customizable keyboard shortcut (including Tab, F-keys, key combos), volume control,
 * form/input protection, two-tier persistence (MySQL User Profile + localStorage),
 * visual overlay with immediate Stop Alarm control, and responsive settings UI.
 * 
 * Version: 3.0.0 Tactical Life-Safety Release
 */

const EmergencyAlertSystem = (function() {
    'use strict';

    // ─────────────────────────────────────────────────────────────
    // Configuration & State
    // ─────────────────────────────────────────────────────────────
    const DEFAULTS = {
        enabled: true,
        shortcut: 'Tab',
        volume: 80, // 0 to 100 percent
        soundProfile: 'SIREN' // 'SIREN' (Heavy Industrial Siren) or 'MORSE'
    };

    let audioContext = null;
    let isAlarmPlaying = false;
    let isTestMode = false;
    let activeOscillators = [];
    let sosLoopTimeout = null;
    let audioUnlockNotified = false;
    let htmlAudioElement = null;

    // Cache preferences in memory
    let config = {
        enabled: DEFAULTS.enabled,
        shortcut: DEFAULTS.shortcut,
        volume: DEFAULTS.volume,
        soundProfile: DEFAULTS.soundProfile
    };

    // ─────────────────────────────────────────────────────────────
    // Persistence Layer (Backend API + LocalStorage Fallback)
    // ─────────────────────────────────────────────────────────────
    function loadSavedPreferences() {
        // 1. Initial synchronous hydration from localStorage
        try {
            const savedEnabled = localStorage.getItem('emergencyShortcutEnabled');
            const savedShortcut = localStorage.getItem('emergencyShortcut');
            const savedVolume = localStorage.getItem('emergencySoundVolume');
            const savedProfile = localStorage.getItem('emergencySoundProfile');

            if (savedEnabled !== null) {
                config.enabled = savedEnabled === 'true' || savedEnabled === true;
            }
            if (savedShortcut && savedShortcut.trim()) {
                config.shortcut = savedShortcut.trim();
            }
            if (savedVolume !== null) {
                const vol = parseInt(savedVolume, 10);
                if (!isNaN(vol)) config.volume = Math.max(0, Math.min(100, vol));
            }
            if (savedProfile && (savedProfile === 'SIREN' || savedProfile === 'MORSE')) {
                config.soundProfile = savedProfile;
            }
        } catch (e) {
            console.warn('[EmergencySOS] LocalStorage read failed:', e);
        }

        // 2. Asynchronous sync with Spring Boot backend if authenticated
        const token = localStorage.getItem('token') || localStorage.getItem('authToken') || localStorage.getItem('jwtToken');
        if (token) {
            fetch('/api/auth/sos-settings', {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + token,
                    'Accept': 'application/json'
                }
            })
            .then(res => {
                if (res.ok) return res.json();
                return null;
            })
            .then(data => {
                if (data && (data.sosShortcutKey || data.status === 'success')) {
                    if (typeof data.sosShortcutEnabled === 'boolean') {
                        config.enabled = data.sosShortcutEnabled;
                        localStorage.setItem('emergencyShortcutEnabled', String(config.enabled));
                    }
                    if (data.sosShortcutKey) {
                        config.shortcut = data.sosShortcutKey;
                        localStorage.setItem('emergencyShortcut', config.shortcut);
                    }
                    if (typeof data.sosSoundVolume === 'number') {
                        config.volume = Math.max(0, Math.min(100, data.sosSoundVolume));
                        localStorage.setItem('emergencySoundVolume', String(config.volume));
                    }
                    updateAllSettingsUIs();
                }
            })
            .catch(err => {
                console.log('[EmergencySOS] Running with local preferences cache.');
            });
        }
    }

    async function savePreferences(newConfig) {
        if (typeof newConfig.enabled === 'boolean') config.enabled = newConfig.enabled;
        if (newConfig.shortcut && newConfig.shortcut.trim()) config.shortcut = newConfig.shortcut.trim();
        if (typeof newConfig.volume === 'number') config.volume = Math.max(0, Math.min(100, newConfig.volume));
        if (newConfig.soundProfile) config.soundProfile = newConfig.soundProfile;

        // Save to localStorage immediately
        localStorage.setItem('emergencyShortcutEnabled', String(config.enabled));
        localStorage.setItem('emergencyShortcut', config.shortcut);
        localStorage.setItem('emergencySoundVolume', String(config.volume));
        localStorage.setItem('emergencySoundProfile', config.soundProfile);

        // Save to backend if authenticated
        const token = localStorage.getItem('token') || localStorage.getItem('authToken') || localStorage.getItem('jwtToken');
        if (token) {
            try {
                const res = await fetch('/api/auth/sos-settings', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + token
                    },
                    body: JSON.stringify({
                        sosShortcutEnabled: config.enabled,
                        sosShortcutKey: config.shortcut,
                        sosSoundVolume: config.volume
                    })
                });
                if (!res.ok) {
                    console.warn('[EmergencySOS] Backend sync returned status:', res.status);
                }
            } catch (e) {
                console.warn('[EmergencySOS] Backend sync failed, settings saved locally:', e);
            }
        }

        updateAllSettingsUIs();
        return config;
    }

    async function resetToDefaults() {
        config.enabled = DEFAULTS.enabled;
        config.shortcut = DEFAULTS.shortcut;
        config.volume = DEFAULTS.volume;

        localStorage.setItem('emergencyShortcutEnabled', String(config.enabled));
        localStorage.setItem('emergencyShortcut', config.shortcut);
        localStorage.setItem('emergencySoundVolume', String(config.volume));

        const token = localStorage.getItem('token') || localStorage.getItem('authToken') || localStorage.getItem('jwtToken');
        if (token) {
            try {
                await fetch('/api/auth/sos-settings/reset', {
                    method: 'POST',
                    headers: {
                        'Authorization': 'Bearer ' + token
                    }
                });
            } catch (e) {
                console.warn('[EmergencySOS] Backend reset sync failed:', e);
            }
        }

        updateAllSettingsUIs();
        return config;
    }

    // ─────────────────────────────────────────────────────────────
    // Audio Engine: Web Audio API Morse Code SOS Generator
    // Pattern: [ . . . ] (dit dit dit)
    //          [ - - - ] (dah dah dah)
    //          [ . . . ] (dit dit dit)
    // ─────────────────────────────────────────────────────────────
    function initAudioContext() {
        if (!audioContext) {
            const AudioCtx = window.AudioContext || window.webkitAudioContext;
            if (AudioCtx) {
                audioContext = new AudioCtx();
            }
        }
        if (audioContext && audioContext.state === 'suspended') {
            audioContext.resume().catch(() => {});
        }
        return audioContext;
    }

    // HTML5 Audio Asset Engine (Dual Acoustic Architecture)
    function playAudioFile(singleShot = false) {
        try {
            if (!htmlAudioElement) {
                htmlAudioElement = new Audio('/audio/emergency-siren.wav');
                htmlAudioElement.preload = 'auto';
            }
            htmlAudioElement.loop = !singleShot;
            htmlAudioElement.volume = Math.max(0, Math.min(100, config.volume)) / 100;
            htmlAudioElement.currentTime = 0;
            const playPromise = htmlAudioElement.play();
            if (playPromise !== undefined) {
                playPromise.catch(err => {
                    console.warn('[EmergencySOS] HTML5 Audio play deferred by browser policy:', err);
                    checkAudioUnlock();
                });
            }
        } catch (e) {
            console.warn('[EmergencySOS] HTML5 Audio playback error:', e);
        }
    }

    function stopAudioFile() {
        if (htmlAudioElement) {
            try {
                htmlAudioElement.pause();
                htmlAudioElement.currentTime = 0;
            } catch (e) {}
        }
    }

    // Ensures audio context is unlocked by a user gesture
    function checkAudioUnlock() {
        if (!audioUnlockNotified) {
            audioUnlockNotified = true;
            if (window.ToastManager) {
                ToastManager.info('Click anywhere on screen to activate emergency sound siren.', 4000);
            }
            const unlockHandler = function() {
                if (audioContext && audioContext.state === 'suspended') {
                    audioContext.resume().catch(() => {});
                }
                if (isAlarmPlaying && htmlAudioElement && htmlAudioElement.paused) {
                    htmlAudioElement.play().catch(() => {});
                }
                document.removeEventListener('click', unlockHandler);
                document.removeEventListener('keydown', unlockHandler);
            };
            document.addEventListener('click', unlockHandler, { once: true });
            document.addEventListener('keydown', unlockHandler, { once: true });
        }
    }

    /**
     * Plays a single Morse tone pulse (dit or dah) at designated time
     */
    function scheduleTone(startTime, duration, targetGain) {
        if (!audioContext || audioContext.state === 'closed') return;

        try {
            // Dual oscillator: 850 Hz fundamental + 1275 Hz overtone for piercing timbre
            const osc1 = audioContext.createOscillator();
            const osc2 = audioContext.createOscillator();
            const gainNode = audioContext.createGain();

            osc1.type = 'sawtooth';
            osc1.frequency.setValueAtTime(850, startTime);

            osc2.type = 'sine';
            osc2.frequency.setValueAtTime(1275, startTime);

            // Shape amplitude envelope: smooth attack & decay (5ms)
            gainNode.gain.setValueAtTime(0.0001, startTime);
            gainNode.gain.linearRampToValueAtTime(targetGain, startTime + 0.005);
            gainNode.gain.setValueAtTime(targetGain, startTime + duration - 0.005);
            gainNode.gain.linearRampToValueAtTime(0.0001, startTime + duration);

            osc1.connect(gainNode);
            osc2.connect(gainNode);
            gainNode.connect(audioContext.destination);

            osc1.start(startTime);
            osc2.start(startTime);

            osc1.stop(startTime + duration);
            osc2.stop(startTime + duration);

            activeOscillators.push(osc1, osc2);

            // Clean up reference on finish
            osc1.onended = function() {
                const i1 = activeOscillators.indexOf(osc1);
                if (i1 !== -1) activeOscillators.splice(i1, 1);
                const i2 = activeOscillators.indexOf(osc2);
                if (i2 !== -1) activeOscillators.splice(i2, 1);
            };
        } catch (err) {
            console.error('[EmergencySOS] Tone scheduling error:', err);
        }
    }

    /**
     * Executes one complete SOS Morse sequence:
     * 3 dits (S), 3 dahs (O), 3 dits (S), followed by repeat gap
     */
    function playSosCycle(singleShot = false) {
        if (!isAlarmPlaying) return;

        const ctx = initAudioContext();
        if (!ctx) return;

        checkAudioUnlock();

        const ditDuration = 0.12; // 120 ms
        const dahDuration = 0.36; // 360 ms
        const elementGap = 0.12;  // between dits/dahs
        const letterGap = 0.36;   // between letters (S and O)
        const wordGap = 0.95;     // repeat pause

        // Max safe gain clamped to 0.40
        const normalizedVol = Math.max(0, Math.min(100, config.volume)) / 100;
        const targetGain = normalizedVol * 0.40;

        let curTime = ctx.currentTime + 0.05;

        // 1. Letter 'S': 3 dits (. . .)
        for (let i = 0; i < 3; i++) {
            scheduleTone(curTime, ditDuration, targetGain);
            curTime += ditDuration + (i < 2 ? elementGap : letterGap);
        }

        // 2. Letter 'O': 3 dahs (- - -)
        for (let i = 0; i < 3; i++) {
            scheduleTone(curTime, dahDuration, targetGain);
            curTime += dahDuration + (i < 2 ? elementGap : letterGap);
        }

        // 3. Letter 'S': 3 dits (. . .)
        for (let i = 0; i < 3; i++) {
            scheduleTone(curTime, ditDuration, targetGain);
            curTime += ditDuration + (i < 2 ? elementGap : wordGap);
        }

        const totalCycleMs = Math.round((curTime - ctx.currentTime) * 1000);

        if (singleShot) {
            sosLoopTimeout = setTimeout(() => {
                stopAlarm();
            }, totalCycleMs);
        } else {
            // Repeat continuously
            sosLoopTimeout = setTimeout(() => {
                if (isAlarmPlaying) {
                    playActiveCycle(false);
                }
            }, totalCycleMs);
        }
    }

    /**
     * Executes heavy industrial emergency siren & naval shipyard launch warning alarm.
     * Layered synthesis: Sub-bass rumble (110Hz), dual detuned sweeping siren (420Hz-850Hz),
     * and harmonic naval horn chord (330Hz + 495Hz).
     */
    function playIndustrialSirenCycle(singleShot = false) {
        if (!isAlarmPlaying) return;

        const ctx = initAudioContext();
        if (!ctx) return;

        checkAudioUnlock();

        const normalizedVol = Math.max(0, Math.min(100, config.volume)) / 100;
        const targetGain = normalizedVol * 0.42;

        const now = ctx.currentTime + 0.05;
        const cycleDuration = 3.2; // 3.2s per industrial siren revolution

        try {
            // 1. Sub-bass rumble oscillator (110 Hz low-pass)
            const subOsc = ctx.createOscillator();
            const subFilter = ctx.createBiquadFilter();
            const subGain = ctx.createGain();

            subOsc.type = 'sawtooth';
            subOsc.frequency.setValueAtTime(110, now);
            subFilter.type = 'lowpass';
            subFilter.frequency.setValueAtTime(180, now);

            subGain.gain.setValueAtTime(0.0001, now);
            subGain.gain.linearRampToValueAtTime(targetGain * 0.35, now + 0.2);
            subGain.gain.setValueAtTime(targetGain * 0.35, now + cycleDuration - 0.25);
            subGain.gain.linearRampToValueAtTime(0.0001, now + cycleDuration);

            subOsc.connect(subFilter);
            subFilter.connect(subGain);
            subGain.connect(ctx.destination);

            subOsc.start(now);
            subOsc.stop(now + cycleDuration);
            activeOscillators.push(subOsc);

            // 2. Dual detuned industrial centrifugal siren (420 Hz -> 840 Hz -> 420 Hz)
            const siren1 = ctx.createOscillator();
            const siren2 = ctx.createOscillator();
            const sirenGain = ctx.createGain();

            siren1.type = 'sawtooth';
            siren2.type = 'sawtooth';

            siren1.frequency.setValueAtTime(420, now);
            siren1.frequency.exponentialRampToValueAtTime(840, now + 1.7);
            siren1.frequency.exponentialRampToValueAtTime(420, now + cycleDuration);

            siren2.frequency.setValueAtTime(424, now);
            siren2.frequency.exponentialRampToValueAtTime(848, now + 1.7);
            siren2.frequency.exponentialRampToValueAtTime(424, now + cycleDuration);

            sirenGain.gain.setValueAtTime(0.0001, now);
            sirenGain.gain.linearRampToValueAtTime(targetGain * 0.65, now + 0.25);
            sirenGain.gain.setValueAtTime(targetGain * 0.65, now + cycleDuration - 0.2);
            sirenGain.gain.linearRampToValueAtTime(0.0001, now + cycleDuration);

            siren1.connect(sirenGain);
            siren2.connect(sirenGain);
            sirenGain.connect(ctx.destination);

            siren1.start(now);
            siren1.stop(now + cycleDuration);
            siren2.start(now);
            siren2.stop(now + cycleDuration);
            activeOscillators.push(siren1, siren2);

            // 3. Staggered naval horn klaxon (330 Hz + 495 Hz)
            const horn1 = ctx.createOscillator();
            const horn2 = ctx.createOscillator();
            const hornGain = ctx.createGain();

            horn1.type = 'triangle';
            horn2.type = 'sine';
            horn1.frequency.setValueAtTime(330, now);
            horn2.frequency.setValueAtTime(495, now);

            const hornGainVal = Math.max(0.0001, targetGain * 0.40);
            hornGain.gain.setValueAtTime(hornGainVal, now);
            hornGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.65);

            horn1.connect(hornGain);
            horn2.connect(hornGain);
            hornGain.connect(ctx.destination);

            horn1.start(now);
            horn1.stop(now + 0.65);
            horn2.start(now);
            horn2.stop(now + 0.65);
            activeOscillators.push(horn1, horn2);

            // 4. Trigger vibration if supported
            if (navigator.vibrate) {
                try { navigator.vibrate([350, 150, 350, 150, 600]); } catch (e) {}
            }
        } catch (err) {
            console.error('[EmergencySOS] Industrial siren error:', err);
        }

        const cycleMs = Math.round(cycleDuration * 1000);
        if (singleShot) {
            sosLoopTimeout = setTimeout(() => {
                stopAlarm();
            }, cycleMs);
        } else {
            sosLoopTimeout = setTimeout(() => {
                if (isAlarmPlaying) {
                    playActiveCycle(false);
                }
            }, cycleMs);
        }
    }

    /**
     * Dispatches active sound profile
     */
    function playActiveCycle(singleShot = false) {
        if (config.soundProfile === 'MORSE') {
            playSosCycle(singleShot);
        } else {
            playIndustrialSirenCycle(singleShot);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Public Trigger & Stop Methods
    // ─────────────────────────────────────────────────────────────
    async function broadcastSosEvent(action, status) {
        try {
            const token = localStorage.getItem('token') || localStorage.getItem('authToken');
            const headers = { 'Content-Type': 'application/json' };
            if (token) headers['Authorization'] = 'Bearer ' + token;

            await fetch('/api/auth/sos-event', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({
                    action: action,
                    status: status,
                    details: 'Emergency SOS siren toggled (Shortcut: ' + config.shortcut + ', Vol: ' + config.volume + '%)'
                })
            });
        } catch (e) {
            console.warn('[EmergencySOS] Telemetry broadcast notice:', e.message);
        }
    }

    function triggerAlarm() {
        if (isAlarmPlaying) return;

        isAlarmPlaying = true;
        isTestMode = false;

        const ctx = initAudioContext();
        if (ctx && ctx.state === 'suspended') {
            ctx.resume().catch(() => {});
        }

        showVisualAlertOverlay(false);
        updateAllSettingsUIs();

        // 1. Play high-definition continuous WAV siren asset
        playAudioFile(false);

        // 2. Synthesize acoustic reinforcement oscillators
        playActiveCycle(false);

        // 3. Dispatch persistent backend emergency telemetry
        broadcastSosEvent('SOS_ALARM_ACTIVATED', 'ACTIVE');

        if (window.ToastManager) {
            ToastManager.warning('Emergency SOS alarm activated! Press STOP ALARM to silence.', 5000);
        }
    }

    function testAlarm() {
        if (isAlarmPlaying) {
            stopAlarm();
            return;
        }

        isAlarmPlaying = true;
        isTestMode = true;

        const ctx = initAudioContext();
        if (ctx && ctx.state === 'suspended') {
            ctx.resume().catch(() => {});
        }

        showVisualAlertOverlay(true);
        updateAllSettingsUIs();

        playAudioFile(true);
        playActiveCycle(true); // single shot test

        if (window.ToastManager) {
            ToastManager.info('Playing SOS sound pattern test...', 3500);
        }
    }

    function stopAlarm() {
        isAlarmPlaying = false;
        isTestMode = false;

        stopAudioFile();

        if (sosLoopTimeout) {
            clearTimeout(sosLoopTimeout);
            sosLoopTimeout = null;
        }

        // Immediately stop all active oscillators
        activeOscillators.forEach(osc => {
            try {
                osc.stop();
                osc.disconnect();
            } catch (e) {}
        });
        activeOscillators = [];

        hideVisualAlertOverlay();
        updateAllSettingsUIs();

        // Dispatch persistent backend emergency telemetry
        broadcastSosEvent('SOS_ALARM_SILENCED', 'SILENCED');
    }

    // ─────────────────────────────────────────────────────────────
    // Keyboard Event Handling & Safe Input Protection
    // ─────────────────────────────────────────────────────────────
    function isInputFocused(event) {
        const target = event.target || document.activeElement;
        if (!target) return false;

        const tag = target.tagName ? target.tagName.toUpperCase() : '';
        if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
        if (target.isContentEditable || target.getAttribute('contenteditable') === 'true') return true;
        if (target.closest && target.closest('input, textarea, select, [contenteditable="true"]')) return true;

        return false;
    }

    function normalizeKey(event) {
        let key = event.key;
        if (!key) return '';

        // Standardize key names
        if (key === ' ' || key === 'Spacebar') key = 'Space';
        if (key === 'Esc') key = 'Escape';

        // Check modifiers
        let combo = '';
        if (event.ctrlKey && key !== 'Control') combo += 'Ctrl+';
        if (event.altKey && key !== 'Alt') combo += 'Alt+';
        if (event.shiftKey && key !== 'Shift') combo += 'Shift+';

        combo += key;
        return combo;
    }

    function matchShortcut(event) {
        if (!config.enabled) return false;

        const targetKey = (config.shortcut || 'Tab').trim();
        const pressedKey = event.key;

        // 1. Direct exact key match (e.g. "Tab", "F6", "F8", "F12")
        if (targetKey.toLowerCase() === 'tab' && pressedKey === 'Tab' && !event.ctrlKey && !event.altKey && !event.metaKey) {
            return true;
        }
        if (targetKey.toUpperCase() === pressedKey.toUpperCase() && !event.ctrlKey && !event.altKey && !event.metaKey) {
            return true;
        }

        // 2. Combo match (e.g. "Ctrl+Shift+S", "Alt+E")
        const currentCombo = normalizeKey(event);
        if (currentCombo.toLowerCase() === targetKey.toLowerCase()) {
            return true;
        }

        return false;
    }

    function handleGlobalKeyDown(event) {
        // Prevent key-hold repeat machine-gunning
        if (event.repeat) return;

        // If alarm overlay is open and user presses Escape or Space, silence the alarm
        if (isAlarmPlaying && (event.key === 'Escape' || event.key === 'Esc')) {
            event.preventDefault();
            stopAlarm();
            return;
        }

        // Do not intercept if shortcut is disabled
        if (!config.enabled) return;

        // Crucial safety rule: Never trigger while typing in inputs, textareas, or form fields!
        if (isInputFocused(event)) {
            // Normal Tab navigation in forms must proceed without interruption!
            return;
        }

        if (matchShortcut(event)) {
            // Intercept and trigger emergency sound
            event.preventDefault();
            event.stopPropagation();

            if (isAlarmPlaying) {
                stopAlarm();
            } else {
                triggerAlarm();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Visual Alert Interface (Tactical Overlay)
    // ─────────────────────────────────────────────────────────────
    function ensureOverlayExists() {
        let overlay = document.getElementById('emergency-alert-indicator');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'emergency-alert-indicator';
            overlay.setAttribute('role', 'alertdialog');
            overlay.setAttribute('aria-modal', 'true');
            overlay.setAttribute('aria-label', 'Emergency SOS Activated');

            overlay.innerHTML = `
                <div class="emergency-alert-container">
                    <div class="emergency-alert-beacon">
                        <i class="fas fa-radiation fa-spin"></i>
                    </div>
                    <div class="emergency-alert-badge">
                        <i class="fas fa-exclamation-triangle"></i> ACTIVE ALARM INDICATOR
                    </div>
                    <h1 class="emergency-alert-title">EMERGENCY SOS ACTIVATED</h1>
                    <p class="emergency-alert-status">
                        Emergency sound is playing.
                    </p>
                    <div class="emergency-alert-meta" id="emergencyOverlayMeta">
                        Triggered via shortcut: <kbd class="sos-kbd">TAB</kbd>
                    </div>
                    <div class="emergency-alert-actions">
                        <button type="button" class="btn-sos-stop" id="btnStopEmergencyAlarm" onclick="EmergencyAlertSystem.stopAlarm()">
                            <i class="fas fa-volume-mute"></i> STOP ALARM
                        </button>
                        <button type="button" class="btn-sos-dismiss" id="btnDismissEmergencyAlarm" onclick="EmergencyAlertSystem.stopAlarm()">
                            <i class="fas fa-times"></i> DISMISS
                        </button>
                    </div>
                    <p class="emergency-alert-notice">
                        <i class="fas fa-info-circle"></i> Stopping the local alarm does not submit or cancel an official emergency dispatch report.
                    </p>
                </div>
            `;
            overlay.addEventListener('click', function(e) {
                if (e.target === overlay) {
                    EmergencyAlertSystem.stopAlarm();
                }
            });
            document.body.appendChild(overlay);
            injectStyles();
        }
        return overlay;
    }

    function showVisualAlertOverlay(isTest = false) {
        const overlay = ensureOverlayExists();
        const metaEl = document.getElementById('emergencyOverlayMeta');
        if (metaEl) {
            metaEl.innerHTML = isTest 
                ? `Mode: <span style="color:#38bdf8;font-weight:700;">TEST SOUND AUDITION</span> (Auto-stops in 3.6s)`
                : `Triggered via shortcut: <kbd class="sos-kbd">${escapeHtml(config.shortcut.toUpperCase())}</kbd>`;
        }
        overlay.classList.add('visible');
    }

    function hideVisualAlertOverlay() {
        const overlay = document.getElementById('emergency-alert-indicator');
        if (overlay) {
            overlay.classList.remove('visible');
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Reusable Emergency SOS Settings UI (Cards & Modal)
    // ─────────────────────────────────────────────────────────────
    function injectStyles() {
        if (document.getElementById('emergency-sos-system-styles')) return;

        const style = document.createElement('style');
        style.id = 'emergency-sos-system-styles';
        style.textContent = `
            /* Header stays accessible above overlay so header STOP button always works */
            .cc-hud-header, .cc-header, #sosSoundBtn {
                position: relative !important;
                z-index: 1000001 !important;
            }
            /* Overlay */
            #emergency-alert-indicator {
                position: fixed;
                inset: 0;
                background: rgba(15, 23, 42, 0.88);
                backdrop-filter: blur(6px);
                -webkit-backdrop-filter: blur(6px);
                display: none;
                align-items: center;
                justify-content: center;
                z-index: 999999;
                padding: 1.5rem;
                animation: sos-fade-in 0.2s ease-out;
            }
            #emergency-alert-indicator.visible {
                display: flex !important;
            }
            @keyframes sos-fade-in {
                from { opacity: 0; transform: scale(0.98); }
                to { opacity: 1; transform: scale(1); }
            }

            .emergency-alert-container {
                max-width: 580px;
                width: 100%;
                background: #0f172a;
                border: 2px solid #ef4444;
                box-shadow: 0 0 50px rgba(239, 68, 68, 0.5), inset 0 0 20px rgba(239, 68, 68, 0.2);
                border-radius: 20px;
                padding: 2.5rem 2rem;
                text-align: center;
                color: #ffffff;
                position: relative;
                animation: sos-pulse-border 1.2s infinite alternate;
            }
            @keyframes sos-pulse-border {
                0% { border-color: #ef4444; box-shadow: 0 0 30px rgba(239, 68, 68, 0.4); }
                100% { border-color: #f87171; box-shadow: 0 0 60px rgba(239, 68, 68, 0.8), inset 0 0 30px rgba(239, 68, 68, 0.3); }
            }

            .emergency-alert-beacon {
                font-size: 3.5rem;
                color: #ef4444;
                margin-bottom: 0.8rem;
                filter: drop-shadow(0 0 15px rgba(239, 68, 68, 0.8));
            }
            .emergency-alert-badge {
                display: inline-flex;
                align-items: center;
                gap: 0.5rem;
                background: rgba(239, 68, 68, 0.2);
                border: 1px solid rgba(239, 68, 68, 0.5);
                color: #fca5a5;
                font-family: 'JetBrains Mono', monospace;
                font-size: 0.85rem;
                font-weight: 700;
                padding: 0.35rem 0.9rem;
                border-radius: 9999px;
                letter-spacing: 0.08em;
                margin-bottom: 1rem;
            }
            .emergency-alert-title {
                font-size: 2.2rem;
                font-weight: 800;
                color: #ffffff;
                letter-spacing: -0.02em;
                margin-bottom: 0.5rem;
                text-transform: uppercase;
            }
            .emergency-alert-status {
                color: #cbd5e1;
                font-size: 1.15rem;
                margin-bottom: 1.2rem;
            }
            .emergency-alert-meta {
                background: rgba(30, 41, 59, 0.8);
                border: 1px solid #334155;
                border-radius: 10px;
                padding: 0.75rem 1.2rem;
                display: inline-block;
                color: #94a3b8;
                font-size: 0.95rem;
                margin-bottom: 1.8rem;
            }
            .sos-kbd {
                background: #1e293b;
                border: 1px solid #475569;
                border-bottom: 2px solid #64748b;
                padding: 0.2rem 0.6rem;
                border-radius: 6px;
                color: #38bdf8;
                font-family: 'JetBrains Mono', monospace;
                font-weight: 700;
            }

            .emergency-alert-actions {
                display: flex;
                gap: 1rem;
                justify-content: center;
                flex-wrap: wrap;
                margin-bottom: 1.5rem;
            }
            .btn-sos-stop {
                background: #ef4444;
                color: #ffffff;
                border: none;
                border-radius: 12px;
                padding: 1rem 2.2rem;
                font-size: 1.15rem;
                font-weight: 700;
                cursor: pointer;
                display: inline-flex;
                align-items: center;
                gap: 0.6rem;
                box-shadow: 0 4px 20px rgba(239, 68, 68, 0.4);
                transition: all 0.15s ease;
            }
            .btn-sos-stop:hover {
                background: #dc2626;
                transform: translateY(-2px);
                box-shadow: 0 6px 25px rgba(239, 68, 68, 0.6);
            }
            .btn-sos-dismiss {
                background: #334155;
                color: #e2e8f0;
                border: 1px solid #475569;
                border-radius: 12px;
                padding: 1rem 1.8rem;
                font-size: 1.05rem;
                font-weight: 600;
                cursor: pointer;
                display: inline-flex;
                align-items: center;
                gap: 0.5rem;
                transition: all 0.15s ease;
            }
            .btn-sos-dismiss:hover {
                background: #475569;
            }
            .emergency-alert-notice {
                color: #64748b;
                font-size: 0.85rem;
                line-height: 1.4;
                margin: 0;
            }

            /* Settings Component Styles */
            .sos-settings-card {
                background: rgba(15, 23, 42, 0.75);
                border: 1px solid rgba(239, 68, 68, 0.35);
                border-radius: 16px;
                padding: 1.6rem;
                margin-top: 1.5rem;
                color: #f1f5f9;
                box-shadow: 0 4px 20px rgba(0,0,0,0.3);
            }
            .sos-settings-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                flex-wrap: wrap;
                gap: 0.75rem;
                margin-bottom: 1.2rem;
                padding-bottom: 0.8rem;
                border-bottom: 1px solid rgba(255,255,255,0.08);
            }
            .sos-settings-title {
                display: flex;
                align-items: center;
                gap: 0.6rem;
                font-size: 1.25rem;
                font-weight: 700;
                color: #ef4444;
                margin: 0;
            }
            .sos-status-pill {
                display: inline-flex;
                align-items: center;
                gap: 0.4rem;
                padding: 0.3rem 0.75rem;
                border-radius: 9999px;
                font-size: 0.8rem;
                font-weight: 700;
                text-transform: uppercase;
                letter-spacing: 0.05em;
            }
            .sos-status-pill.enabled {
                background: rgba(16, 185, 129, 0.15);
                color: #34d399;
                border: 1px solid rgba(16, 185, 129, 0.4);
            }
            .sos-status-pill.disabled {
                background: rgba(100, 116, 139, 0.15);
                color: #94a3b8;
                border: 1px solid rgba(100, 116, 139, 0.4);
            }
            .sos-status-pill.active {
                background: rgba(239, 68, 68, 0.25);
                color: #f87171;
                border: 1px solid rgba(239, 68, 68, 0.7);
                animation: sos-pulse-badge 1s infinite alternate;
            }
            @keyframes sos-pulse-badge {
                from { transform: scale(1); }
                to { transform: scale(1.05); }
            }

            .sos-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
                gap: 1.2rem;
                margin-bottom: 1.4rem;
            }
            .sos-field {
                background: rgba(30, 41, 59, 0.6);
                border: 1px solid #334155;
                border-radius: 12px;
                padding: 1rem;
            }
            .sos-field-label {
                color: #94a3b8;
                font-size: 0.85rem;
                font-weight: 600;
                margin-bottom: 0.5rem;
                display: flex;
                align-items: center;
                justify-content: space-between;
            }
            .sos-shortcut-display {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: 0.5rem;
            }
            .sos-badge-key {
                background: #0f172a;
                border: 1px solid #475569;
                border-bottom: 3px solid #38bdf8;
                padding: 0.5rem 1rem;
                border-radius: 8px;
                font-size: 1.15rem;
                font-family: 'JetBrains Mono', monospace;
                font-weight: 700;
                color: #38bdf8;
                letter-spacing: 0.05em;
            }

            /* Custom toggle switch */
            .sos-toggle-wrap {
                display: flex;
                align-items: center;
                gap: 0.75rem;
            }
            .sos-switch {
                position: relative;
                display: inline-block;
                width: 48px;
                height: 26px;
            }
            .sos-switch input {
                opacity: 0;
                width: 0;
                height: 0;
            }
            .sos-slider {
                position: absolute;
                cursor: pointer;
                top: 0; left: 0; right: 0; bottom: 0;
                background-color: #334155;
                transition: .25s;
                border-radius: 26px;
            }
            .sos-slider:before {
                position: absolute;
                content: "";
                height: 20px;
                width: 20px;
                left: 3px;
                bottom: 3px;
                background-color: white;
                transition: .25s;
                border-radius: 50%;
            }
            .sos-switch input:checked + .sos-slider {
                background-color: #10b981;
            }
            .sos-switch input:checked + .sos-slider:before {
                transform: translateX(22px);
            }

            /* Volume range slider */
            .sos-vol-slider {
                width: 100%;
                accent-color: #ef4444;
                cursor: pointer;
                margin-top: 0.4rem;
            }

            /* Buttons */
            .sos-btn {
                padding: 0.6rem 1.1rem;
                border-radius: 8px;
                font-size: 0.9rem;
                font-weight: 600;
                border: none;
                cursor: pointer;
                display: inline-flex;
                align-items: center;
                gap: 0.45rem;
                transition: all 0.15s ease;
            }
            .sos-btn-change {
                background: #0284c7;
                color: #ffffff;
            }
            .sos-btn-change:hover {
                background: #0369a1;
            }
            .sos-btn-test {
                background: #dc2626;
                color: #ffffff;
            }
            .sos-btn-test:hover {
                background: #b91c1c;
            }
            .sos-btn-save {
                background: #059669;
                color: #ffffff;
            }
            .sos-btn-save:hover {
                background: #047857;
            }
            .sos-btn-reset {
                background: #475569;
                color: #ffffff;
            }
            .sos-btn-reset:hover {
                background: #334155;
            }

            /* Key capture box */
            .sos-capture-box {
                background: #0f172a;
                border: 2px dashed #38bdf8;
                border-radius: 10px;
                padding: 1rem;
                margin-top: 1rem;
                text-align: center;
                display: none;
            }
            .sos-capture-input {
                width: 100%;
                background: #1e293b;
                border: 1px solid #475569;
                color: #38bdf8;
                font-family: 'JetBrains Mono', monospace;
                font-size: 1.1rem;
                font-weight: 700;
                padding: 0.7rem;
                border-radius: 8px;
                text-align: center;
                cursor: pointer;
            }
            .sos-capture-hint {
                color: #94a3b8;
                font-size: 0.8rem;
                margin-top: 0.5rem;
            }
        `;
        document.head.appendChild(style);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    /**
     * Generates HTML markup for an in-page Emergency SOS settings card
     */
    function generateSettingsHtml(instanceId = 'global') {
        const statusClass = isAlarmPlaying ? 'active' : (config.enabled ? 'enabled' : 'disabled');
        const statusText = isAlarmPlaying ? 'ALARM PLAYING' : (config.enabled ? 'ENABLED' : 'DISABLED');

        return `
            <div class="sos-settings-card" id="sosCard_${instanceId}">
                <div class="sos-settings-header">
                    <h3 class="sos-settings-title">
                        <i class="fas fa-bullhorn"></i> Emergency SOS Sound & Shortcut Settings
                    </h3>
                    <span class="sos-status-pill ${statusClass}" id="sosStatusPill_${instanceId}">
                        <i class="fas fa-circle"></i> ${statusText}
                    </span>
                </div>
                
                <p style="color: #94a3b8; font-size: 0.9rem; margin-bottom: 1.2rem; line-height: 1.45;">
                    Configure a personal emergency shortcut (e.g. <strong>Tab</strong>, <strong>F8</strong>, <strong>F12</strong>) to trigger a loud, continuous Morse code SOS alarm. Protected from accidental activation while typing in forms.
                </p>

                <div class="sos-grid">
                    <!-- 1. Enable / Disable Toggle -->
                    <div class="sos-field">
                        <div class="sos-field-label">
                            <span>Emergency Shortcut Status</span>
                            <i class="fas fa-power-off"></i>
                        </div>
                        <div class="sos-toggle-wrap">
                            <label class="sos-switch">
                                <input type="checkbox" id="sosEnableToggle_${instanceId}" ${config.enabled ? 'checked' : ''} onchange="EmergencyAlertSystem.onToggleEnabled('${instanceId}', this.checked)">
                                <span class="sos-slider"></span>
                            </label>
                            <span id="sosToggleLabel_${instanceId}" style="font-weight: 600; color: ${config.enabled ? '#34d399' : '#94a3b8'};">
                                ${config.enabled ? 'Shortcut Active' : 'Shortcut Disabled'}
                            </span>
                        </div>
                    </div>

                    <!-- 2. Shortcut Key & Change Button -->
                    <div class="sos-field">
                        <div class="sos-field-label">
                            <span>Configured Shortcut</span>
                            <i class="fas fa-keyboard"></i>
                        </div>
                        <div class="sos-shortcut-display">
                            <span class="sos-badge-key" id="sosCurrentKey_${instanceId}">
                                ${escapeHtml(config.shortcut.toUpperCase())}
                            </span>
                            <button type="button" class="sos-btn sos-btn-change" onclick="EmergencyAlertSystem.openCaptureBox('${instanceId}')">
                                <i class="fas fa-edit"></i> Change
                            </button>
                        </div>
                    </div>

                    <!-- 3. Sound Volume Control -->
                    <div class="sos-field">
                        <div class="sos-field-label">
                            <span>Alarm Volume</span>
                            <span id="sosVolValue_${instanceId}" style="color: #ef4444; font-weight: 700;">${config.volume}%</span>
                        </div>
                        <input type="range" class="sos-vol-slider" id="sosVolSlider_${instanceId}" min="0" max="100" value="${config.volume}" 
                               oninput="EmergencyAlertSystem.onVolumeInput('${instanceId}', this.value)">
                    </div>

                    <!-- 4. Sound Profile Selection -->
                    <div class="sos-field" style="grid-column: 1 / -1;">
                        <div class="sos-field-label">
                            <span>Acoustic Profile & Atmosphere</span>
                            <i class="fas fa-bullhorn"></i>
                        </div>
                        <select class="sos-profile-select" id="sosProfileSelect_${instanceId}" 
                                style="width:100%;background:#0b1322;color:#f8fafc;border:1px solid rgba(255,255,255,0.2);padding:0.55rem 0.8rem;border-radius:6px;font-size:0.85rem;"
                                onchange="EmergencyAlertSystem.onProfileChange('${instanceId}', this.value)">
                            <option value="SIREN" ${config.soundProfile === 'SIREN' ? 'selected' : ''}>🚨 Industrial Emergency Siren & Naval Shipyard Launch Warning</option>
                            <option value="MORSE" ${config.soundProfile === 'MORSE' ? 'selected' : ''}>📻 Tactical Maritime Morse Code SOS Klaxon (... --- ...)</option>
                        </select>
                    </div>
                </div>

                <!-- Key Capture Section -->
                <div class="sos-capture-box" id="sosCaptureBox_${instanceId}">
                    <div style="font-weight: 600; color: #f8fafc; margin-bottom: 0.5rem;">
                        <i class="fas fa-crosshairs"></i> Press desired key (e.g., Tab, F6, F7, F8, F12, or key combo)
                    </div>
                    <input type="text" class="sos-capture-input" id="sosCaptureInput_${instanceId}" readonly 
                           placeholder="Press desired key..." onkeydown="EmergencyAlertSystem.onCaptureKeyDown('${instanceId}', event)">
                    <div class="sos-capture-hint">
                        Tip: Tab is supported and safely isolated from form inputs. Press Escape or Cancel to close.
                    </div>
                    <div style="display: flex; gap: 0.5rem; justify-content: center; margin-top: 0.8rem;">
                        <button type="button" class="sos-btn sos-btn-save" onclick="EmergencyAlertSystem.confirmCapture('${instanceId}')">
                            <i class="fas fa-check"></i> Apply Key
                        </button>
                        <button type="button" class="sos-btn sos-btn-reset" onclick="EmergencyAlertSystem.cancelCapture('${instanceId}')">
                            <i class="fas fa-times"></i> Cancel
                        </button>
                    </div>
                </div>

                <!-- Action Controls -->
                <div style="display: flex; gap: 0.6rem; flex-wrap: wrap; justify-content: space-between; align-items: center; margin-top: 1.2rem; padding-top: 1rem; border-top: 1px solid rgba(255,255,255,0.08);">
                    <div style="display: flex; gap: 0.6rem; flex-wrap: wrap;">
                        <button type="button" class="sos-btn sos-btn-test" id="sosBtnTest_${instanceId}" onclick="EmergencyAlertSystem.testAlarm()">
                            <i class="fas ${isAlarmPlaying ? 'fa-stop-circle' : 'fa-volume-up'}"></i> ${isAlarmPlaying ? 'Stop Test' : 'Test Sound'}
                        </button>
                        <button type="button" class="sos-btn sos-btn-reset" onclick="EmergencyAlertSystem.onResetClick('${instanceId}')">
                            <i class="fas fa-undo"></i> Reset to Default
                        </button>
                    </div>
                    <button type="button" class="sos-btn sos-btn-save" onclick="EmergencyAlertSystem.onSaveClick('${instanceId}')">
                        <i class="fas fa-save"></i> Save Settings
                    </button>
                </div>
            </div>
        `;
    }

    function renderSettingsCard(containerId, instanceId = 'page') {
        const container = document.getElementById(containerId);
        if (container) {
            injectStyles();
            container.innerHTML = generateSettingsHtml(instanceId);
            updateAllSettingsUIs();
        }
    }

    function openSettingsModal() {
        injectStyles();
        let modalOverlay = document.getElementById('emergency-sos-modal-overlay');
        if (!modalOverlay) {
            modalOverlay = document.createElement('div');
            modalOverlay.id = 'emergency-sos-modal-overlay';
            modalOverlay.style.cssText = `
                position: fixed; inset: 0; background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(6px);
                z-index: 99999; display: flex; align-items: center; justify-content: center; padding: 1.5rem;
            `;
            modalOverlay.innerHTML = `
                <div style="max-width: 650px; width: 100%; position: relative;">
                    <button type="button" onclick="EmergencyAlertSystem.closeSettingsModal()" 
                            style="position: absolute; right: 1rem; top: 1rem; z-index: 10; background: #334155; color: white; border: none; border-radius: 50%; width: 32px; height: 32px; cursor: pointer;">
                        <i class="fas fa-times"></i>
                    </button>
                    <div id="emergencyModalContent"></div>
                </div>
            `;
            document.body.appendChild(modalOverlay);
        }

        const content = document.getElementById('emergencyModalContent');
        if (content) {
            content.innerHTML = generateSettingsHtml('modal');
        }
        modalOverlay.style.display = 'flex';
        updateAllSettingsUIs();
    }

    function closeSettingsModal() {
        const modal = document.getElementById('emergency-sos-modal-overlay');
        if (modal) modal.style.display = 'none';
    }

    // UI Event Handlers
    function onToggleEnabled(instanceId, isChecked) {
        config.enabled = isChecked;
        const label = document.getElementById(`sosToggleLabel_${instanceId}`);
        if (label) {
            label.textContent = isChecked ? 'Shortcut Active' : 'Shortcut Disabled';
            label.style.color = isChecked ? '#34d399' : '#94a3b8';
        }
        updateAllSettingsUIs();
    }

    function onVolumeInput(instanceId, val) {
        config.volume = parseInt(val, 10) || 0;
        const valLabel = document.getElementById(`sosVolValue_${instanceId}`);
        if (valLabel) valLabel.textContent = config.volume + '%';
    }

    function openCaptureBox(instanceId) {
        const box = document.getElementById(`sosCaptureBox_${instanceId}`);
        const input = document.getElementById(`sosCaptureInput_${instanceId}`);
        if (box && input) {
            box.style.display = 'block';
            input.value = config.shortcut;
            input.focus();
        }
    }

    function onCaptureKeyDown(instanceId, event) {
        // Prevent default tab/navigation behavior inside capture box so key can be set
        event.preventDefault();
        event.stopPropagation();

        let key = event.key;
        if (!key) return;

        if (key === 'Escape' || key === 'Esc') {
            cancelCapture(instanceId);
            return;
        }

        if (key === 'Control' || key === 'Alt' || key === 'Shift' || key === 'Meta') {
            return; // Ignore raw modifiers
        }

        let combo = '';
        if (event.ctrlKey) combo += 'Ctrl+';
        if (event.altKey) combo += 'Alt+';
        if (event.shiftKey) combo += 'Shift+';

        if (key === ' ' || key === 'Spacebar') key = 'Space';

        combo += key;

        const input = document.getElementById(`sosCaptureInput_${instanceId}`);
        if (input) {
            input.value = combo;
        }
    }

    function confirmCapture(instanceId) {
        const input = document.getElementById(`sosCaptureInput_${instanceId}`);
        if (input && input.value.trim()) {
            config.shortcut = input.value.trim();
        }
        cancelCapture(instanceId);
        updateAllSettingsUIs();
        if (window.ToastManager) {
            ToastManager.info('Shortcut set to: ' + config.shortcut + '. Click "Save Settings" to persist.', 3500);
        }
    }

    function cancelCapture(instanceId) {
        const box = document.getElementById(`sosCaptureBox_${instanceId}`);
        if (box) box.style.display = 'none';
    }

    async function onSaveClick(instanceId) {
        try {
            await savePreferences(config);
            if (window.ToastManager) {
                ToastManager.success('✓ Emergency SOS settings successfully saved to profile & cloud.', 3500);
            } else {
                alert('✓ Emergency SOS settings saved successfully.');
            }
        } catch (e) {
            console.error(e);
            if (window.ToastManager) {
                ToastManager.error('Failed to save settings: ' + e.message, 3500);
            }
        }
    }

    async function onResetClick(instanceId) {
        await resetToDefaults();
        if (window.ToastManager) {
            ToastManager.info('Emergency SOS settings reset to default (Tab, 80%, Enabled).', 3500);
        }
    }

    function updateAllSettingsUIs() {
        const instances = ['global', 'page', 'modal', 'profile'];
        const statusClass = isAlarmPlaying ? 'active' : (config.enabled ? 'enabled' : 'disabled');
        const statusText = isAlarmPlaying ? 'ALARM PLAYING' : (config.enabled ? 'ENABLED' : 'DISABLED');

        instances.forEach(id => {
            const pill = document.getElementById(`sosStatusPill_${id}`);
            if (pill) {
                pill.className = `sos-status-pill ${statusClass}`;
                pill.innerHTML = `<i class="fas fa-circle"></i> ${statusText}`;
            }

            const keyEl = document.getElementById(`sosCurrentKey_${id}`);
            if (keyEl) keyEl.textContent = config.shortcut.toUpperCase();

            const toggle = document.getElementById(`sosEnableToggle_${id}`);
            if (toggle) toggle.checked = config.enabled;

            const toggleLabel = document.getElementById(`sosToggleLabel_${id}`);
            if (toggleLabel) {
                toggleLabel.textContent = config.enabled ? 'Shortcut Active' : 'Shortcut Disabled';
                toggleLabel.style.color = config.enabled ? '#34d399' : '#94a3b8';
            }

            const slider = document.getElementById(`sosVolSlider_${id}`);
            if (slider) slider.value = config.volume;

            const volValue = document.getElementById(`sosVolValue_${id}`);
            if (volValue) volValue.textContent = config.volume + '%';

            const testBtn = document.getElementById(`sosBtnTest_${id}`);
            if (testBtn) {
                testBtn.innerHTML = isAlarmPlaying ? '<i class="fas fa-stop"></i> Stop Sound' : '<i class="fas fa-volume-up"></i> Test Sound';
            }

            const profileSelect = document.getElementById(`sosProfileSelect_${id}`);
            if (profileSelect) profileSelect.value = config.soundProfile || 'SIREN';
        });
    }

    function onProfileChange(instanceId, profile) {
        if (profile === 'SIREN' || profile === 'MORSE') {
            config.soundProfile = profile;
            localStorage.setItem('emergencySoundProfile', profile);
            if (window.ToastManager) {
                ToastManager.info(`Acoustic Profile switched to ${profile === 'SIREN' ? 'Industrial Emergency Siren' : 'Maritime Morse Code SOS'}.`, 3000);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // System Initialization
    // ─────────────────────────────────────────────────────────────
    function init() {
        injectStyles();
        loadSavedPreferences();

        // Register global keyboard shortcut listener on document (capture phase)
        document.addEventListener('keydown', handleGlobalKeyDown, true);

        // Auto-check audio unlock on first user interaction
        const userAction = function() {
            initAudioContext();
            document.removeEventListener('click', userAction);
            document.removeEventListener('keydown', userAction);
        };
        document.addEventListener('click', userAction, { once: true });
        document.addEventListener('keydown', userAction, { once: true });

        console.log(`[EmergencySOS] Initialized. Shortcut: [${config.shortcut}], Enabled: [${config.enabled}], Volume: [${config.volume}%]`);
    }

    // Auto-init on DOM Ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────
    return {
        init: init,
        triggerAlarm: triggerAlarm,
        playAlarm: triggerAlarm,
        testAlarm: testAlarm,
        stopAlarm: stopAlarm,
        isPlaying: function() { return isAlarmPlaying; },
        getConfig: function() { return Object.assign({}, config); },
        getShortcut: function() { return config.shortcut; },
        setShortcut: function(k) { config.shortcut = k; savePreferences(config); },
        savePreferences: savePreferences,
        resetToDefaults: resetToDefaults,
        renderSettingsCard: renderSettingsCard,
        openSettingsModal: openSettingsModal,
        closeSettingsModal: closeSettingsModal,
        onToggleEnabled: onToggleEnabled,
        onVolumeInput: onVolumeInput,
        openCaptureBox: openCaptureBox,
        onCaptureKeyDown: onCaptureKeyDown,
        confirmCapture: confirmCapture,
        cancelCapture: cancelCapture,
        onSaveClick: onSaveClick,
        onResetClick: onResetClick,
        onProfileChange: onProfileChange
    };

})();

// Expose on global window object in browsers
if (typeof window !== 'undefined') {
    window.EmergencyAlertSystem = EmergencyAlertSystem;
}
if (typeof globalThis !== 'undefined') {
    globalThis.EmergencyAlertSystem = EmergencyAlertSystem;
}

// Export for module systems if present
if (typeof module !== 'undefined' && module.exports) {
    module.exports = EmergencyAlertSystem;
}
