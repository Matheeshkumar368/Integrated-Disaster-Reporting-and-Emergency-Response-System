/**
 * Emergency Sound Alert System
 * Provides a customizable keyboard shortcut to emit a loud alarm sound
 * for individuals who may be unable to verbally call for help.
 */

const EmergencyAlertSystem = (function() {
    // Audio context for generating alarm sounds
    let audioContext = null;
    let isAlarmPlaying = false;
    let currentOscillators = [];
    
    // Default emergency shortcut (F12 key)
    const DEFAULT_SHORTCUT = 'F12';
    
    // Get user's configured shortcut from localStorage
    function getShortcut() {
        return localStorage.getItem('emergencyShortcut') || DEFAULT_SHORTCUT;
    }
    
    // Save user's preferred shortcut to localStorage
    function setShortcut(shortcut) {
        localStorage.setItem('emergencyShortcut', shortcut);
    }
    
    // Initialize Web Audio API context
    function initAudio() {
        if (!audioContext) {
            audioContext = new (window.AudioContext || window.webkitAudioContext)();
        }
        // Resume audio context if suspended (browser autoplay policy)
        if (audioContext.state === 'suspended') {
            audioContext.resume();
        }
    }
    
    // Generate a loud, attention-grabbing alarm sound
    function playAlarm() {
        if (isAlarmPlaying) {
            stopAlarm();
            return;
        }
        
        initAudio();
        isAlarmPlaying = true;
        
        // Create a loud, alternating siren sound
        const frequencies = [880, 1100, 1320, 880]; // A5, C#6, E6, A5 - emergency frequencies
        
        // Main oscillator - loud siren
        const osc1 = audioContext.createOscillator();
        const gain1 = audioContext.createGain();
        
        osc1.type = 'sawtooth';
        osc1.frequency.setValueAtTime(frequencies[0], audioContext.currentTime);
        
        // Frequency modulation for siren effect
        osc1.frequency.linearRampToValueAtTime(frequencies[1], audioContext.currentTime + 0.25);
        osc1.frequency.linearRampToValueAtTime(frequencies[2], audioContext.currentTime + 0.5);
        osc1.frequency.linearRampToValueAtTime(frequencies[3], audioContext.currentTime + 0.75);
        
        // Very loud gain (max volume for emergency)
        gain1.gain.setValueAtTime(0.8, audioContext.currentTime);
        
        // Add some modulation to make it more attention-grabbing
        const lfo = audioContext.createOscillator();
        const lfoGain = audioContext.createGain();
        lfo.frequency.value = 4; // 4 Hz modulation
        lfoGain.gain.value = 0.3;
        
        lfo.connect(lfoGain);
        lfoGain.connect(gain1.gain);
        
        osc1.connect(gain1);
        gain1.connect(audioContext.destination);
        
        // Second oscillator for richer sound
        const osc2 = audioContext.createOscillator();
        const gain2 = audioContext.createGain();
        
        osc2.type = 'square';
        osc2.frequency.setValueAtTime(frequencies[0] * 2, audioContext.currentTime);
        osc2.frequency.linearRampToValueAtTime(frequencies[1] * 2, audioContext.currentTime + 0.25);
        osc2.frequency.linearRampToValueAtTime(frequencies[2] * 2, audioContext.currentTime + 0.5);
        osc2.frequency.linearRampToValueAtTime(frequencies[3] * 2, audioContext.currentTime + 0.75);
        
        gain2.gain.setValueAtTime(0.4, audioContext.currentTime);
        
        osc2.connect(gain2);
        gain2.connect(audioContext.destination);
        
        // Start all oscillators
        osc1.start();
        osc2.start();
        lfo.start();
        
        currentOscillators = [osc1, osc2, lfo];
        
        // Continue the alarm pattern
        const alarmLoop = () => {
            if (!isAlarmPlaying) return;
            
            const now = audioContext.currentTime;
            osc1.frequency.setValueAtTime(frequencies[0], now);
            osc1.frequency.linearRampToValueAtTime(frequencies[1], now + 0.25);
            osc1.frequency.linearRampToValueAtTime(frequencies[2], now + 0.5);
            osc1.frequency.linearRampToValueAtTime(frequencies[3], now + 0.75);
            
            osc2.frequency.setValueAtTime(frequencies[0] * 2, now);
            osc2.frequency.linearRampToValueAtTime(frequencies[1] * 2, now + 0.25);
            osc2.frequency.linearRampToValueAtTime(frequencies[2] * 2, now + 0.5);
            osc2.frequency.linearRampToValueAtTime(frequencies[3] * 2, now + 0.75);
            
            // Continue looping
            if (isAlarmPlaying) {
                setTimeout(alarmLoop, 750);
            }
        };
        
        setTimeout(alarmLoop, 750);
        
        // Show visual indicator
        showVisualAlert();
    }
    
    // Stop the alarm sound
    function stopAlarm() {
        isAlarmPlaying = false;
        
        // Stop all oscillators
        currentOscillators.forEach(osc => {
            try {
                osc.stop();
            } catch (e) {
                // Oscillator may already be stopped
            }
        });
        currentOscillators = [];
        
        // Hide visual indicator
        hideVisualAlert();
    }
    
    // Show visual alert when alarm is playing
    function showVisualAlert() {
        // Create or update the emergency indicator
        let indicator = document.getElementById('emergency-alert-indicator');
        if (!indicator) {
            indicator = document.createElement('div');
            indicator.id = 'emergency-alert-indicator';
            indicator.innerHTML = `
                <div class="emergency-alert-box">
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>EMERGENCY ALERT ACTIVE</span>
                    <button onclick="EmergencyAlertSystem.stopAlarm()">STOP ALARM</button>
                </div>
            `;
            document.body.appendChild(indicator);
            
            // Add styles if not already added
            if (!document.getElementById('emergency-alert-styles')) {
                const styles = document.createElement('style');
                styles.id = 'emergency-alert-styles';
                styles.textContent = `
                    #emergency-alert-indicator {
                        position: fixed;
                        top: 0;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        background: rgba(220, 38, 38, 0.95);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        z-index: 99999;
                        animation: emergency-flash 0.5s infinite;
                    }
                    @keyframes emergency-flash {
                        0%, 100% { background: rgba(220, 38, 38, 0.95); }
                        50% { background: rgba(185, 28, 28, 0.95); }
                    }
                    .emergency-alert-box {
                        text-align: center;
                        color: white;
                    }
                    .emergency-alert-box i {
                        font-size: 5rem;
                        margin-bottom: 1rem;
                        animation: emergency-pulse 0.3s infinite;
                    }
                    @keyframes emergency-pulse {
                        0%, 100% { transform: scale(1); }
                        50% { transform: scale(1.1); }
                    }
                    .emergency-alert-box span {
                        display: block;
                        font-size: 2.5rem;
                        font-weight: bold;
                        margin-bottom: 2rem;
                        text-transform: uppercase;
                    }
                    .emergency-alert-box button {
                        padding: 1rem 3rem;
                        font-size: 1.5rem;
                        font-weight: bold;
                        background: white;
                        color: #dc2626;
                        border: none;
                        border-radius: 8px;
                        cursor: pointer;
                        transition: transform 0.2s;
                    }
                    .emergency-alert-box button:hover {
                        transform: scale(1.05);
                    }
                `;
                document.head.appendChild(styles);
            }
        }
        indicator.style.display = 'flex';
    }
    
    // Hide visual alert
    function hideVisualAlert() {
        const indicator = document.getElementById('emergency-alert-indicator');
        if (indicator) {
            indicator.style.display = 'none';
        }
    }
    
    // Handle keyboard shortcut
    function handleKeyDown(event) {
        const shortcut = getShortcut();
        const pressedKey = event.key;
        
        // Check if pressed key matches configured shortcut
        if (pressedKey === shortcut || 
            (shortcut.startsWith('Ctrl+') && event.ctrlKey && pressedKey === shortcut.replace('Ctrl+', '')) ||
            (shortcut.startsWith('Alt+') && event.altKey && pressedKey === shortcut.replace('Alt+', '')) ||
            (shortcut.startsWith('Shift+') && event.shiftKey && pressedKey === shortcut.replace('Shift+', ''))) {
            
            // Prevent default behavior
            event.preventDefault();
            
            // Play/stop alarm
            playAlarm();
        }
    }
    
    // Initialize the system
    function init() {
        // Add keyboard event listener
        document.addEventListener('keydown', handleKeyDown, true);
        
        // Log for debugging
        console.log('Emergency Alert System initialized. Shortcut: ' + getShortcut());
    }
    
    // Test the alarm sound (for user verification)
    function testAlarm() {
        playAlarm();
        // Auto-stop after 3 seconds for testing
        setTimeout(() => {
            if (isAlarmPlaying) {
                stopAlarm();
            }
        }, 3000);
    }
    
    // Public API
    return {
        init: init,
        playAlarm: playAlarm,
        stopAlarm: stopAlarm,
        getShortcut: getShortcut,
        setShortcut: setShortcut,
        testAlarm: testAlarm,
        isPlaying: function() { return isAlarmPlaying; }
    };
})();

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', EmergencyAlertSystem.init);
} else {
    EmergencyAlertSystem.init();
}
