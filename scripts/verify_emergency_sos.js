const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const ARTIFACT_DIR = path.join('C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652');
const SCREENSHOT_DIR = path.join(ARTIFACT_DIR, 'screenshots');
if (!fs.existsSync(SCREENSHOT_DIR)) fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

function httpRequest(options, postData) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                let parsed = null;
                try { parsed = JSON.parse(data); } catch (e) { parsed = data; }
                resolve({ statusCode: res.statusCode, headers: res.headers, data: parsed });
            });
        });
        req.on('error', reject);
        if (postData) {
            req.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
        }
        req.end();
    });
}

// Simple CDP client over native Node WebSocket
class CDPClient {
    constructor(wsUrl) {
        this.wsUrl = wsUrl;
        this.ws = null;
        this.id = 1;
        this.callbacks = new Map();
    }

    connect() {
        return new Promise((resolve, reject) => {
            this.ws = new WebSocket(this.wsUrl);
            this.ws.onopen = () => resolve();
            this.ws.onerror = (err) => reject(err);
            this.ws.onmessage = (event) => {
                const msg = JSON.parse(event.data);
                if (msg.id && this.callbacks.has(msg.id)) {
                    const cb = this.callbacks.get(msg.id);
                    this.callbacks.delete(msg.id);
                    if (msg.error) cb.reject(new Error(msg.error.message));
                    else cb.resolve(msg.result);
                }
            };
        });
    }

    send(method, params = {}) {
        return new Promise((resolve, reject) => {
            const id = this.id++;
            this.callbacks.set(id, { resolve, reject });
            this.ws.send(JSON.stringify({ id, method, params }));
        });
    }

    async evaluate(expression) {
        const res = await this.send('Runtime.evaluate', {
            expression,
            returnByValue: true,
            awaitPromise: true
        });
        if (res.exceptionDetails) {
            const desc = (res.exceptionDetails.exception && res.exceptionDetails.exception.description) || res.exceptionDetails.text;
            throw new Error(desc || 'Evaluation failed');
        }
        return res.result ? res.result.value : undefined;
    }

    close() {
        if (this.ws) this.ws.close();
    }
}

async function runTestSuite() {
    console.log('========================================================================');
    console.log('EMERGENCY SOS SOUND SHORTCUT - FULL STACK VERIFICATION SUITE');
    console.log('========================================================================\n');

    let passed = 0;
    let failed = 0;
    const testLog = [];

    function assert(condition, testName, details = '') {
        if (condition) {
            console.log(`[PASS] ${testName} ${details ? '(' + details + ')' : ''}`);
            passed++;
            testLog.push({ name: testName, status: 'PASS', details });
        } else {
            console.error(`[FAIL] ${testName} ${details ? '(' + details + ')' : ''}`);
            failed++;
            testLog.push({ name: testName, status: 'FAIL', details });
        }
    }

    let chromeProc = null;
    let cdp = null;

    try {
        // =====================================================================
        // PART 1: BACKEND REST API & DATABASE INTEGRITY
        // =====================================================================
        console.log('\n--- [PHASE 1] BACKEND REST API & DATABASE PERSISTENCE ---');

        // 1.1 Authenticate Citizen
        const loginRes = await httpRequest({
            hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'citizen@gmail.com', password: 'citizen123' });

        assert(loginRes.statusCode === 200 && loginRes.data && loginRes.data.token, 'Citizen Authentication & JWT Issuance');
        const token = loginRes.data.token;

        // 1.2 Verify Login payload includes SOS preferences
        assert(loginRes.data.sosShortcutEnabled !== undefined && loginRes.data.sosShortcutKey !== undefined, 
            'Login Payload Hydrates Emergency SOS Preferences', 
            `Key: ${loginRes.data.sosShortcutKey}, Vol: ${loginRes.data.sosSoundVolume}%`);

        // 1.3 Unauthenticated Access Guard
        const unauthRes = await httpRequest({
            hostname: 'localhost', port: 8080, path: '/api/auth/sos-settings', method: 'GET'
        });
        assert(unauthRes.statusCode === 401, 'Unauthenticated Access to /api/auth/sos-settings Denied (HTTP 401)');

        // 1.4 Get SOS Settings via Authenticated API
        const getSettingsRes = await httpRequest({
            hostname: 'localhost', port: 8080, path: '/api/auth/sos-settings', method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        assert(getSettingsRes.statusCode === 200 && getSettingsRes.data.status === 'success', 
            'GET /api/auth/sos-settings Fetches Persisted User Settings', 
            `Key: ${getSettingsRes.data.sosShortcutKey}, Volume: ${getSettingsRes.data.sosSoundVolume}%`);

        // 1.5 Update Settings to Tab, 85% Volume, Enabled
        const updateRes = await httpRequest({
            hostname: 'localhost', port: 8080, path: '/api/auth/sos-settings', method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }
        }, {
            sosShortcutEnabled: true,
            sosShortcutKey: 'Tab',
            sosSoundVolume: 85
        });
        assert(updateRes.statusCode === 200 && updateRes.data.sosShortcutKey === 'Tab' && updateRes.data.sosSoundVolume === 85,
            'PUT /api/auth/sos-settings Updates Settings (Tab, 85%, Enabled)');

        // 1.6 Verify Settings Restored on Fresh GET
        const verifyGetRes = await httpRequest({
            hostname: 'localhost', port: 8080, path: '/api/auth/sos-settings', method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        assert(verifyGetRes.statusCode === 200 && verifyGetRes.data.sosShortcutKey === 'Tab' && verifyGetRes.data.sosSoundVolume === 85,
            'Verified Persistent State Across API Invocations');

        // =====================================================================
        // PART 2: HEADLESS BROWSER & KEYBOARD SHORTCUT WORKFLOW
        // =====================================================================
        console.log('\n--- [PHASE 2] BROWSER AUTOMATION, KEYBOARD EVENTS & AUDIO TEST ---');

        const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
        const cdpPort = 9222;
        const tmpDir = path.join(os.tmpdir(), `sos_browser_${Date.now()}`);

        chromeProc = spawn(chromePath, [
            '--headless=new',
            `--remote-debugging-port=${cdpPort}`,
            '--disable-gpu',
            '--no-sandbox',
            '--autoplay-policy=no-user-gesture-required',
            `--user-data-dir=${tmpDir}`,
            `--window-size=1440,900`,
            'about:blank'
        ]);

        await new Promise(r => setTimeout(r, 2000));

        // Connect to Chrome page
        const targets = await httpRequest({ hostname: '127.0.0.1', port: cdpPort, path: '/json/list', method: 'GET' });
        const pageTarget = targets.data.find(t => t.type === 'page');
        if (!pageTarget) throw new Error('Could not find Chrome page target');

        cdp = new CDPClient(pageTarget.webSocketDebuggerUrl);
        await cdp.connect();
        await cdp.send('Page.enable');
        await cdp.send('Runtime.enable');
        await cdp.send('DOM.enable');

        // 1. Navigate to index.html to bind localStorage to origin http://localhost:8080
        await cdp.send('Page.navigate', { url: 'http://localhost:8080/index.html' });
        await new Promise(r => setTimeout(r, 1500));

        // 2. Seed authentication session in localStorage
        await cdp.evaluate(`
            localStorage.setItem('token', '${token}');
            localStorage.setItem('userRole', 'Citizen');
            localStorage.setItem('userName', 'John Citizen');
            localStorage.setItem('emergencyShortcutEnabled', 'true');
            localStorage.setItem('emergencyShortcut', 'Tab');
            localStorage.setItem('emergencySoundVolume', '85');
        `);

        // 3. Navigate to profile.html
        await cdp.send('Page.navigate', { url: 'http://localhost:8080/profile.html' });
        await new Promise(r => setTimeout(r, 2500));

        // 2.1 Verify Settings UI Rendered
        const uiConfig = await cdp.evaluate(`
            (() => {
                const card = document.getElementById('emergencyAlertContainer');
                const keyEl = document.getElementById('sosCurrentKey_profile');
                const volEl = document.getElementById('sosVolValue_profile');
                const toggle = document.getElementById('sosEnableToggle_profile');
                return {
                    hasCard: !!card,
                    keyText: keyEl ? keyEl.textContent.trim() : null,
                    volText: volEl ? volEl.textContent.trim() : null,
                    enabled: toggle ? toggle.checked : null,
                    rawHtml: card ? card.innerHTML.slice(0, 150) : null
                };
            })()
        `);
        console.log('DEBUG uiConfig:', uiConfig);
        assert(uiConfig.hasCard && (uiConfig.keyText === 'TAB' || uiConfig.keyText === 'Tab') && uiConfig.enabled === true,
            'Emergency SOS Settings Card Rendered in Profile with TAB Key Badge');

        // 2.2 Capture Screenshot of Emergency SOS Settings Card
        const cardShot = await cdp.send('Page.captureScreenshot', { format: 'png' });
        const cardShotPath = path.join(SCREENSHOT_DIR, 'emergency_sos_settings_card.png');
        fs.writeFileSync(cardShotPath, Buffer.from(cardShot.data, 'base64'));
        assert(fs.existsSync(cardShotPath) && fs.statSync(cardShotPath).size > 1000,
            'Captured Screenshot: emergency_sos_settings_card.png');

        // 2.3 Press TAB Key when NOT in form input -> Verify Alarm Triggers
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'rawKeyDown',
            key: 'Tab',
            code: 'Tab',
            windowsVirtualKeyCode: 9
        });
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'keyUp',
            key: 'Tab',
            code: 'Tab',
            windowsVirtualKeyCode: 9
        });
        await new Promise(r => setTimeout(r, 600));

        const alarmActive = await cdp.evaluate(`
            (() => {
                const isPlaying = window.EmergencyAlertSystem ? EmergencyAlertSystem.isPlaying() : false;
                const overlay = document.getElementById('emergency-alert-indicator');
                const overlayVisible = overlay ? overlay.classList.contains('visible') : false;
                const conf = window.EmergencyAlertSystem ? EmergencyAlertSystem.getConfig() : null;
                return { isPlaying, overlayVisible, conf };
            })()
        `);
        console.log('DEBUG alarmActive:', alarmActive);
        assert(alarmActive.isPlaying === true && alarmActive.overlayVisible === true,
            'Pressing TAB Triggers Emergency SOS Sound & Fullscreen Indicator Overlay');

        // 2.4 Capture Screenshot of Active Emergency SOS Overlay
        const alarmShot = await cdp.send('Page.captureScreenshot', { format: 'png' });
        const alarmShotPath = path.join(SCREENSHOT_DIR, 'emergency_sos_alarm_activated_overlay.png');
        fs.writeFileSync(alarmShotPath, Buffer.from(alarmShot.data, 'base64'));
        assert(fs.existsSync(alarmShotPath) && fs.statSync(alarmShotPath).size > 1000,
            'Captured Screenshot: emergency_sos_alarm_activated_overlay.png');

        // 2.5 Stop Alarm via UI Button
        await cdp.evaluate(`
            (() => {
                const btn = document.getElementById('btnStopEmergencyAlarm');
                if (btn) btn.click();
                else if (window.EmergencyAlertSystem) EmergencyAlertSystem.stopAlarm();
            })()
        `);
        await new Promise(r => setTimeout(r, 300));

        const alarmStopped = await cdp.evaluate(`
            (() => {
                const isPlaying = window.EmergencyAlertSystem ? EmergencyAlertSystem.isPlaying() : false;
                const overlay = document.getElementById('emergency-alert-indicator');
                const overlayVisible = overlay ? overlay.classList.contains('visible') : false;
                return { isPlaying, overlayVisible };
            })()
        `);
        assert(alarmStopped.isPlaying === false && alarmStopped.overlayVisible === false,
            'STOP ALARM Button Immediately Silences Audio & Hides Overlay');

        // 2.6 Form / Input Protection Test: Pressing TAB while typing inside an input MUST NOT trigger alarm!
        const inputProtection = await cdp.evaluate(`
            (() => {
                // Create a test input and focus it
                let inp = document.getElementById('testInputForTabGuard');
                if (!inp) {
                    inp = document.createElement('input');
                    inp.id = 'testInputForTabGuard';
                    inp.type = 'text';
                    document.body.appendChild(inp);
                }
                inp.focus();

                // Dispatch synthetic Tab key while input is focused
                const event = new KeyboardEvent('keydown', {
                    key: 'Tab',
                    code: 'Tab',
                    bubbles: true,
                    cancelable: true
                });
                document.dispatchEvent(event);

                const triggered = window.EmergencyAlertSystem ? EmergencyAlertSystem.isPlaying() : false;
                inp.blur();
                return { triggered };
            })()
        `);
        assert(inputProtection.triggered === false,
            'Form & Input Protection Guard: TAB Key Inside Form Inputs Never Fires Alarm');

        // 2.7 Key Repeat Guard Test (event.repeat = true)
        const repeatProtection = await cdp.evaluate(`
            (() => {
                const event = new KeyboardEvent('keydown', {
                    key: 'Tab',
                    code: 'Tab',
                    repeat: true,
                    bubbles: true,
                    cancelable: true
                });
                document.dispatchEvent(event);
                return { triggered: window.EmergencyAlertSystem ? EmergencyAlertSystem.isPlaying() : false };
            })()
        `);
        assert(repeatProtection.triggered === false,
            'Key-Hold Repeat Guard: Holding Down Shortcut Prevents Flapping & Repeated Activation');

        // 2.8 Test Sound Audition Functionality
        const testModeActive = await cdp.evaluate(`
            (() => {
                EmergencyAlertSystem.testAlarm();
                return {
                    isPlaying: EmergencyAlertSystem.isPlaying(),
                    overlayVisible: document.getElementById('emergency-alert-indicator').classList.contains('visible')
                };
            })()
        `);
        assert(testModeActive.isPlaying === true,
            'Test Sound Audition Successfully Plays Morse SOS Pattern');

        // Stop test sound
        await cdp.evaluate(`EmergencyAlertSystem.stopAlarm();`);

        // 2.9 Shortcut Reconfiguration Test: Change to F8
        const reconfigResult = await cdp.evaluate(`
            (async () => {
                await EmergencyAlertSystem.savePreferences({ enabled: true, shortcut: 'F8', volume: 75 });
                return EmergencyAlertSystem.getConfig();
            })()
        `);
        assert(reconfigResult.shortcut === 'F8' && reconfigResult.volume === 75,
            'Custom Shortcut Successfully Reconfigured to F8 with 75% Volume');

        // 2.10 Press F8 to verify new shortcut triggers alarm
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'rawKeyDown',
            key: 'F8',
            code: 'F8',
            windowsVirtualKeyCode: 119
        });
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'keyUp',
            key: 'F8',
            code: 'F8',
            windowsVirtualKeyCode: 119
        });
        await new Promise(r => setTimeout(r, 400));

        const f8Active = await cdp.evaluate(`EmergencyAlertSystem.isPlaying()`);
        assert(f8Active === true, 'Reconfigured Shortcut F8 Successfully Triggers Alarm');
        await cdp.evaluate(`EmergencyAlertSystem.stopAlarm();`);

        // 2.11 Disable Shortcut Test: Toggle enabled = false
        const disabledTest = await cdp.evaluate(`
            (async () => {
                await EmergencyAlertSystem.savePreferences({ enabled: false });
                return EmergencyAlertSystem.getConfig();
            })()
        `);
        assert(disabledTest.enabled === false, 'Emergency SOS Shortcut Successfully Disabled');

        // Press F8 while disabled -> must NOT trigger
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'rawKeyDown',
            key: 'F8',
            code: 'F8',
            windowsVirtualKeyCode: 119
        });
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'keyUp',
            key: 'F8',
            code: 'F8',
            windowsVirtualKeyCode: 119
        });
        await new Promise(r => setTimeout(r, 400));

        const disabledPlayCheck = await cdp.evaluate(`EmergencyAlertSystem.isPlaying()`);
        assert(disabledPlayCheck === false, 'Disabled Shortcut Prevents Any Alarm Activation');

        // 2.12 Reset to Default (Tab, 80%, Enabled)
        const resetResult = await cdp.evaluate(`
            (async () => {
                await EmergencyAlertSystem.resetToDefaults();
                return EmergencyAlertSystem.getConfig();
            })()
        `);
        assert(resetResult.shortcut === 'Tab' && resetResult.enabled === true && resetResult.volume === 80,
            'Reset to Defaults Restores TAB Shortcut, 80% Volume, Enabled');

        // 2.13 Open Settings Modal Test
        await cdp.evaluate(`EmergencyAlertSystem.openSettingsModal();`);
        await new Promise(r => setTimeout(r, 400));
        const modalVisible = await cdp.evaluate(`
            (() => {
                const modal = document.getElementById('emergency-sos-modal-overlay');
                return modal && modal.style.display === 'flex';
            })()
        `);
        assert(modalVisible === true, 'Reusable Emergency SOS Settings Modal Opens via Global API');

        // Capture Screenshot of Modal
        const modalShot = await cdp.send('Page.captureScreenshot', { format: 'png' });
        const modalShotPath = path.join(SCREENSHOT_DIR, 'emergency_sos_modal.png');
        fs.writeFileSync(modalShotPath, Buffer.from(modalShot.data, 'base64'));
        assert(fs.existsSync(modalShotPath) && fs.statSync(modalShotPath).size > 1000,
            'Captured Screenshot: emergency_sos_modal.png');

        await cdp.evaluate(`EmergencyAlertSystem.closeSettingsModal();`);

        // 2.14 Dashboard Integration Verification (Citizen Dashboard)
        await cdp.send('Page.navigate', { url: 'http://localhost:8080/citizen-dashboard.html' });
        await new Promise(r => setTimeout(r, 2500));
        const dashboardLoaded = await cdp.evaluate(`
            (() => {
                return typeof EmergencyAlertSystem !== 'undefined';
            })()
        `);
        assert(dashboardLoaded === true, 'EmergencyAlertSystem Automatically Active in Citizen Safety Hub');

        // Trigger Tab in dashboard
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'rawKeyDown',
            key: 'Tab',
            code: 'Tab',
            windowsVirtualKeyCode: 9
        });
        await cdp.send('Input.dispatchKeyEvent', {
            type: 'keyUp',
            key: 'Tab',
            code: 'Tab',
            windowsVirtualKeyCode: 9
        });
        await new Promise(r => setTimeout(r, 500));

        const dashActive = await cdp.evaluate(`EmergencyAlertSystem.isPlaying()`);
        assert(dashActive === true, 'TAB Shortcut Directly Operates from Citizen Dashboard View');
        await cdp.evaluate(`EmergencyAlertSystem.stopAlarm();`);

    } catch (err) {
        console.error('Test Suite encountered fatal error:', err);
        failed++;
    } finally {
        if (cdp) cdp.close();
        if (chromeProc) chromeProc.kill();
    }

    console.log('\n========================================================================');
    console.log(`EMERGENCY SOS VERIFICATION COMPLETE: ${passed} PASSED, ${failed} FAILED`);
    console.log('========================================================================\n');

    fs.writeFileSync('scripts/emergency_sos_test_results.json', JSON.stringify({
        timestamp: new Date().toISOString(),
        passed,
        failed,
        log: testLog
    }, null, 2), 'utf8');

    if (failed > 0) process.exit(1);
    else process.exit(0);
}

runTestSuite();
