const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const http = require('http');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const CDP_PORT_1 = 9222;
const CDP_PORT_2 = 9223;
const BASE_URL = 'http://localhost:8080';
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots';

if (!fs.existsSync(ARTIFACT_DIR)) {
    fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
}

// Simple CDP client over native WebSocket
class CdpClient {
    constructor(wsUrl) {
        this.wsUrl = wsUrl;
        this.ws = null;
        this.msgId = 0;
        this.callbacks = new Map();
        this.consoleMessages = [];
        this.networkErrors = [];
    }

    async connect() {
        return new Promise((resolve, reject) => {
            this.ws = new globalThis.WebSocket(this.wsUrl);
            this.ws.onopen = () => resolve();
            this.ws.onerror = (err) => reject(err);
            this.ws.onmessage = (event) => {
                const data = JSON.parse(event.data);
                if (data.id && this.callbacks.has(data.id)) {
                    const { resolve, reject } = this.callbacks.get(data.id);
                    this.callbacks.delete(data.id);
                    if (data.error) {
                        reject(new Error(data.error.message || JSON.stringify(data.error)));
                    } else {
                        resolve(data.result);
                    }
                } else if (data.method === 'Runtime.consoleAPICalled') {
                    const args = (data.params.args || []).map(a => a.value || a.description || '').join(' ');
                    this.consoleMessages.push({ type: data.params.type, text: args });
                } else if (data.method === 'Log.entryAdded') {
                    this.consoleMessages.push({ type: data.params.entry.level, text: data.params.entry.text });
                } else if (data.method === 'Network.responseReceived') {
                    const status = data.params.response.status;
                    if (status >= 400 && !data.params.response.url.includes('/favicon.ico')) {
                        this.networkErrors.push({ url: data.params.response.url, status });
                    }
                }
            };
        });
    }

    async send(method, params = {}) {
        const id = ++this.msgId;
        return new Promise((resolve, reject) => {
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
            throw new Error(res.exceptionDetails.text || JSON.stringify(res.exceptionDetails));
        }
        return res.result ? res.result.value : undefined;
    }

    async captureScreenshot(filename) {
        const res = await this.send('Page.captureScreenshot', { format: 'png' });
        const filePath = path.join(ARTIFACT_DIR, filename);
        fs.writeFileSync(filePath, Buffer.from(res.data, 'base64'));
        console.log(`[SCREENSHOT] Saved: ${filename}`);
        return filePath;
    }

    close() {
        if (this.ws) {
            try { this.ws.close(); } catch(e) {}
        }
    }
}

function sleep(ms) {
    return new Promise(res => setTimeout(res, ms));
}

function httpGet(url) {
    return new Promise((resolve, reject) => {
        http.get(url, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => resolve(JSON.parse(data)));
        }).on('error', reject);
    });
}

async function spawnChrome(port, profileDir) {
    const chrome = spawn(CHROME_PATH, [
        '--headless=new',
        `--remote-debugging-port=${port}`,
        `--user-data-dir=${profileDir}`,
        '--disable-gpu',
        '--window-size=1440,900',
        '--no-first-run',
        '--no-default-browser-check',
        'about:blank'
    ], { detached: false });

    await sleep(1500);

    let targets = null;
    for (let i = 0; i < 15; i++) {
        try {
            targets = await httpGet(`http://localhost:${port}/json`);
            if (targets && targets.length > 0) break;
        } catch (e) {
            await sleep(400);
        }
    }

    if (!targets || targets.length === 0) {
        chrome.kill();
        throw new Error(`Failed to connect to Chrome on port ${port}`);
    }

    const pageTarget = targets.find(t => t.type === 'page') || targets[0];
    const client = new CdpClient(pageTarget.webSocketDebuggerUrl);
    await client.connect();

    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await client.send('DOM.enable');
    await client.send('Network.enable');
    await client.send('Log.enable');

    return { chrome, client };
}

async function runBrowserAudit() {
    console.log('========================================================================');
    console.log('STARTING ADVANCED LIVE BROWSER AUDIT: NAVIGATION, SESSIONS & STABILITY');
    console.log('========================================================================\n');

    const testResults = [];
    function record(testName, passed, detail = '') {
        testResults.push({ testName, passed, detail });
        console.log(`[${passed ? 'PASS' : 'FAIL'}] ${testName}${detail ? ' - ' + detail : ''}`);
    }

    const tempDir1 = path.join(process.env.TEMP, 'chrome_session_admin_' + Date.now());
    const tempDir2 = path.join(process.env.TEMP, 'chrome_session_citizen_' + Date.now());

    let sessionAdmin = null;
    let sessionCitizen = null;

    try {
        // =====================================================================
        // AREA 1: AUTHENTICATION ISOLATION & MULTI-SESSION
        // =====================================================================
        console.log('\n--- AREA 1: Authentication Isolation (Concurrent Independent Browser Sessions) ---');
        
        sessionAdmin = await spawnChrome(CDP_PORT_1, tempDir1);
        sessionCitizen = await spawnChrome(CDP_PORT_2, tempDir2);

        // 1.1 Authenticate Session 1 as Admin
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
        await sleep(1500);

        const adminLogin = await sessionAdmin.client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: 'admin@disaster.gov.in', password: 'admin123' })
                });
                const data = await res.json();
                if (res.ok && data.token) {
                    localStorage.setItem('token', data.token);
                    localStorage.setItem('userRole', data.role);
                    localStorage.setItem('userName', data.name);
                    localStorage.setItem('userEmail', 'admin@disaster.gov.in');
                    return { success: true, role: data.role, email: 'admin@disaster.gov.in' };
                }
                return { success: false, status: res.status };
            })()
        `);
        record('Session 1 (Admin) authenticated successfully', adminLogin.success && adminLogin.role === 'Admin');

        // 1.2 Authenticate Session 2 as Citizen
        await sessionCitizen.client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
        await sleep(1500);

        const citizenLogin = await sessionCitizen.client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: 'citizen@gmail.com', password: 'citizen123' })
                });
                const data = await res.json();
                if (res.ok && data.token) {
                    localStorage.setItem('token', data.token);
                    localStorage.setItem('userRole', data.role);
                    localStorage.setItem('userName', data.name);
                    localStorage.setItem('userEmail', 'citizen@gmail.com');
                    return { success: true, role: data.role, email: 'citizen@gmail.com' };
                }
                return { success: false, status: res.status };
            })()
        `);
        record('Session 2 (Citizen) authenticated successfully', citizenLogin.success && citizenLogin.role === 'Citizen');

        // 1.3 Verify Session Isolation: tokens and identities never overwrite each other
        const adminRole = await sessionAdmin.client.evaluate("localStorage.getItem('userRole')");
        const citizenRole = await sessionCitizen.client.evaluate("localStorage.getItem('userRole')");
        const adminEmail = await sessionAdmin.client.evaluate("localStorage.getItem('userEmail')");
        const citizenEmail = await sessionCitizen.client.evaluate("localStorage.getItem('userEmail')");

        record('Session 1 retains Admin credentials independently', adminRole === 'Admin' && adminEmail === 'admin@disaster.gov.in');
        record('Session 2 retains Citizen credentials independently', citizenRole === 'Citizen' && citizenEmail === 'citizen@gmail.com');

        // =====================================================================
        // AREA 2: BROWSER NAVIGATION (BACK, FORWARD, REFRESH, DIRECT URL, ROLE GUARDS)
        // =====================================================================
        console.log('\n--- AREA 2: Browser Navigation, History & Role Interception ---');

        // 2.1 Admin Dashboard load
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/admin-dashboard.html` });
        await sleep(2000);
        const adminDashUrl = await sessionAdmin.client.evaluate('window.location.href');
        const adminTitle = await sessionAdmin.client.evaluate('document.title');
        record('Admin loads Admin Mission Control Dashboard', adminDashUrl.includes('/admin-dashboard.html'), `Title: "${adminTitle}"`);
        await sessionAdmin.client.captureScreenshot('browser_audit_admin_dashboard.png');

        // 2.2 Navigate to Admin Notifications via link / navigation
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/admin-notifications.html` });
        await sleep(2000);
        const adminNotifUrl = await sessionAdmin.client.evaluate('window.location.href');
        record('Admin navigates to Admin Notifications Hub', adminNotifUrl.includes('/admin-notifications.html'));
        await sessionAdmin.client.captureScreenshot('browser_audit_admin_notifications.png');

        // 2.3 Browser Back Button navigation
        await sessionAdmin.client.evaluate('window.history.back()');
        await sleep(2000);
        const backUrl = await sessionAdmin.client.evaluate('window.location.href');
        record('Browser Back button correctly navigates back to Admin Dashboard', backUrl.includes('/admin-dashboard.html'), `Current: ${backUrl}`);

        // 2.4 Browser Forward Button navigation
        await sessionAdmin.client.evaluate('window.history.forward()');
        await sleep(2000);
        const forwardUrl = await sessionAdmin.client.evaluate('window.location.href');
        record('Browser Forward button correctly navigates back to Admin Notifications', forwardUrl.includes('/admin-notifications.html'), `Current: ${forwardUrl}`);

        // 2.5 Browser Page Refresh
        await sessionAdmin.client.send('Page.reload');
        await sleep(2000);
        const refreshedToken = await sessionAdmin.client.evaluate("localStorage.getItem('token')");
        const refreshedRole = await sessionAdmin.client.evaluate("localStorage.getItem('userRole')");
        record('Browser Page Refresh preserves authentication state and role', refreshedToken != null && refreshedRole === 'Admin');

        // 2.6 Direct URL Access & Role Crossover Guard: Citizen attempting to access Admin Mission Control
        console.log('\n--- 2.6 Direct URL Tampering & Role Interception ---');
        await sessionCitizen.client.send('Page.navigate', { url: `${BASE_URL}/admin-dashboard.html` });
        await sleep(2500); // Allow auth guard and redirect to fire
        const citizenRedirectUrl = await sessionCitizen.client.evaluate('window.location.href');
        record(
            'Citizen accessing /admin-dashboard.html is strictly intercepted & routed to Citizen Dashboard',
            citizenRedirectUrl.includes('/citizen-dashboard.html'),
            `Landed on: ${citizenRedirectUrl}`
        );
        await sessionCitizen.client.captureScreenshot('browser_audit_citizen_redirected.png');

        // 2.7 Direct URL Access: Citizen attempting to access Responder Dashboard
        await sessionCitizen.client.send('Page.navigate', { url: `${BASE_URL}/responder-dashboard.html` });
        await sleep(2500);
        const citizenRespUrl = await sessionCitizen.client.evaluate('window.location.href');
        record(
            'Citizen accessing /responder-dashboard.html is strictly intercepted & routed to Citizen Dashboard',
            citizenRespUrl.includes('/citizen-dashboard.html'),
            `Landed on: ${citizenRespUrl}`
        );

        // 2.8 Direct URL Access: Admin attempting to access Citizen Dashboard
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/citizen-dashboard.html` });
        await sleep(2500);
        const adminFromCitizenUrl = await sessionAdmin.client.evaluate('window.location.href');
        record(
            'Admin accessing /citizen-dashboard.html is intercepted & routed back to Mission Control',
            adminFromCitizenUrl.includes('/admin-dashboard.html'),
            `Landed on: ${adminFromCitizenUrl}`
        );

        // 2.9 Unauthenticated Direct URL Access
        console.log('\n--- 2.9 Unauthenticated Direct URL Protection ---');
        // Clear tokens on citizen session to simulate anonymous visitor
        await sessionCitizen.client.evaluate(`
            localStorage.clear();
            sessionStorage.clear();
        `);
        await sessionCitizen.client.send('Page.navigate', { url: `${BASE_URL}/admin-dashboard.html` });
        await sleep(2000);
        const unauthUrl = await sessionCitizen.client.evaluate('window.location.href');
        record(
            'Unauthenticated visitor accessing /admin-dashboard.html is redirected to Login page',
            unauthUrl.includes('/index.html') || unauthUrl.includes('/index-new-login.html') || unauthUrl.endsWith(':8080/'),
            `Redirected to: ${unauthUrl}`
        );

        // 2.10 Logout & Browser Back Stale Cache Protection
        console.log('\n--- 2.10 Logout & Browser Back Stale Session Protection ---');
        // Admin logs out
        await sessionAdmin.client.evaluate(`
            if (window.AuthHelper && window.AuthHelper.logout) {
                window.AuthHelper.logout();
            } else {
                localStorage.clear();
                sessionStorage.clear();
                window.location.href = '/index.html';
            }
        `);
        await sleep(1500);
        const logoutUrl = await sessionAdmin.client.evaluate('window.location.href');
        record('Admin logout clears storage and routes to landing/login', logoutUrl.includes('/index') || logoutUrl.endsWith(':8080/'));

        // Admin presses Back button
        await sessionAdmin.client.evaluate('window.history.back()');
        await sleep(2500);
        const afterBackUrl = await sessionAdmin.client.evaluate('window.location.href');
        const tokenAfterBack = await sessionAdmin.client.evaluate("localStorage.getItem('token')");
        record(
            'Browser Back after logout cannot access protected dashboard (auth guard enforces login)',
            tokenAfterBack == null && (afterBackUrl.includes('/index') || afterBackUrl.endsWith(':8080/')),
            `Current URL: ${afterBackUrl}, Token: ${tokenAfterBack}`
        );

        // =====================================================================
        // AREA 5 & 6: JAVASCRIPT STABILITY & ERROR HANDLING
        // =====================================================================
        console.log('\n--- AREA 5 & 6: JavaScript Stability, Console Health & Error Handling ---');
        
        // Re-authenticate admin and load key pages to check console health
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
        await sleep(1000);
        await sessionAdmin.client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: 'admin@disaster.gov.in', password: 'admin123' })
                });
                const data = await res.json();
                localStorage.setItem('token', data.token);
                localStorage.setItem('userRole', data.role);
                localStorage.setItem('userName', data.name);
                localStorage.setItem('userEmail', data.email);
            })()
        `);
        await sleep(500);

        // Check Admin Mission Control
        sessionAdmin.client.consoleMessages = [];
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/admin-dashboard.html` });
        await sleep(2500);
        const errorsAdminDash = sessionAdmin.client.consoleMessages.filter(m => m.type === 'error');
        record(
            'Admin Dashboard loads with zero fatal script crash errors',
            errorsAdminDash.length === 0,
            `Errors: ${JSON.stringify(errorsAdminDash)}`
        );

        // Check Admin Alerts Console
        sessionAdmin.client.consoleMessages = [];
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/admin-alerts.html` });
        await sleep(2000);
        const errorsAdminAlerts = sessionAdmin.client.consoleMessages.filter(m => m.type === 'error');
        record(
            'Admin Alerts Console loads cleanly',
            errorsAdminAlerts.length === 0,
            `Errors: ${JSON.stringify(errorsAdminAlerts)}`
        );
        await sessionAdmin.client.captureScreenshot('browser_audit_admin_alerts.png');

        // Check Responder Dashboard
        await sessionAdmin.client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: 'responder@disaster.gov.in', password: 'responder123' })
                });
                const data = await res.json();
                localStorage.setItem('token', data.token);
                localStorage.setItem('userRole', data.role);
                localStorage.setItem('userName', data.name);
                localStorage.setItem('userEmail', data.email);
            })()
        `);
        sessionAdmin.client.consoleMessages = [];
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/responder-dashboard.html` });
        await sleep(2500);
        const errorsRespDash = sessionAdmin.client.consoleMessages.filter(m => m.type === 'error');
        record(
            'Responder Dashboard loads cleanly with zero fatal script crashes',
            errorsRespDash.length === 0,
            `Errors: ${JSON.stringify(errorsRespDash)}`
        );
        await sessionAdmin.client.captureScreenshot('browser_audit_responder_dashboard.png');

        // Check Citizen Dashboard
        await sessionAdmin.client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: 'citizen@gmail.com', password: 'citizen123' })
                });
                const data = await res.json();
                localStorage.setItem('token', data.token);
                localStorage.setItem('userRole', data.role);
                localStorage.setItem('userName', data.name);
                localStorage.setItem('userEmail', data.email);
            })()
        `);
        sessionAdmin.client.consoleMessages = [];
        await sessionAdmin.client.send('Page.navigate', { url: `${BASE_URL}/citizen-dashboard.html` });
        await sleep(2500);
        const errorsCitizenDash = sessionAdmin.client.consoleMessages.filter(m => m.type === 'error');
        record(
            'Citizen Dashboard loads cleanly with zero fatal script crashes',
            errorsCitizenDash.length === 0,
            `Errors: ${JSON.stringify(errorsCitizenDash)}`
        );
        await sessionAdmin.client.captureScreenshot('browser_audit_citizen_dashboard.png');

        // Check Citizen Report Submission UI
        const reportBtnExists = await sessionAdmin.client.evaluate("document.getElementById('reportDisasterBtn') != null || document.querySelector('button[type=\"submit\"]') != null");
        record('Citizen Report Submission form controls are initialized in DOM', reportBtnExists === true);

    } catch (err) {
        console.error('[AUDIT EXCEPTION]:', err);
        record('Browser test execution completed without unexpected crash', false, err.message);
    } finally {
        if (sessionAdmin) {
            sessionAdmin.client.close();
            sessionAdmin.chrome.kill();
        }
        if (sessionCitizen) {
            sessionCitizen.client.close();
            sessionCitizen.chrome.kill();
        }
    }

    console.log('\n========================================================================');
    console.log('BROWSER AUDIT SUMMARY');
    console.log('========================================================================');
    const passedCount = testResults.filter(t => t.passed).length;
    const failedCount = testResults.filter(t => !t.passed).length;
    console.log(`Total Tests: ${testResults.length}`);
    console.log(`PASSED: ${passedCount}`);
    console.log(`FAILED: ${failedCount}`);
    console.log(`SUCCESS RATE: ${Math.round((passedCount / testResults.length) * 100)}%\n`);

    const resultPath = path.join(__dirname, 'browser_audit_results.json');
    fs.writeFileSync(resultPath, JSON.stringify(testResults, null, 2));
    console.log(`[RESULTS] Saved results to ${resultPath}`);

    if (failedCount > 0) {
        process.exitCode = 1;
    }
}

runBrowserAudit();
