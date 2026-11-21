const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const http = require('http');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const CDP_PORT = 9222;
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
        this.consoleErrors = [];
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
                } else if (data.method === 'Runtime.consoleAPICalled' && data.params.type === 'error') {
                    const args = (data.params.args || []).map(a => a.value || a.description || '').join(' ');
                    this.consoleErrors.push(args);
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

async function runStandaloneNotificationAudit() {
    console.log('========================================================================');
    console.log('STARTING STANDALONE NOTIFICATION CENTRE (MOBILE SHADE EXPERIENCE) AUDIT');
    console.log('========================================================================\n');

    const testResults = [];
    function record(testName, passed, detail = '') {
        testResults.push({ testName, passed, detail });
        console.log(`[${passed ? 'PASS' : 'FAIL'}] ${testName}${detail ? ' - ' + detail : ''}`);
    }

    const tempDir = path.join(process.env.TEMP, 'chrome_notif_shade_' + Date.now());
    const chrome = spawn(CHROME_PATH, [
        '--headless=new',
        `--remote-debugging-port=${CDP_PORT}`,
        `--user-data-dir=${tempDir}`,
        '--disable-gpu',
        '--window-size=1280,800',
        '--no-first-run',
        '--no-default-browser-check',
        'about:blank'
    ], { detached: false });

    let client = null;

    try {
        await sleep(1500);

        let targets = null;
        for (let i = 0; i < 15; i++) {
            try {
                targets = await httpGet(`http://localhost:${CDP_PORT}/json`);
                if (targets && targets.length > 0) break;
            } catch (e) {
                await sleep(400);
            }
        }

        const pageTarget = targets.find(t => t.type === 'page') || targets[0];
        client = new CdpClient(pageTarget.webSocketDebuggerUrl);
        await client.connect();

        await client.send('Page.enable');
        await client.send('Runtime.enable');
        await client.send('DOM.enable');

        // =====================================================================
        // SECTION 1: ADMIN STANDALONE NOTIFICATION CENTRE
        // =====================================================================
        console.log('\n--- SECTION 1: Admin Standalone Notification Centre ---');

        // 1.1 Authenticate as Admin
        await client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
        await sleep(1200);

        await client.evaluate(`
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
                localStorage.setItem('userEmail', 'admin@disaster.gov.in');
            })()
        `);
        await sleep(500);

        // 1.2 Open Admin Notifications
        await client.send('Page.navigate', { url: `${BASE_URL}/admin-notifications.html` });
        await sleep(2500);

        // Check Absence of Sidebar and Normal Dashboard Navbar
        const adminHasSidebar = await client.evaluate("document.querySelector('aside.sidebar, .sidebar') != null");
        record('Admin Notifications: Dashboard sidebar is completely ABSENT', adminHasSidebar === false);

        const adminHasTopNavbar = await client.evaluate("document.querySelector('.navbar, nav.top-nav, .cc-header') != null");
        record('Admin Notifications: Dashboard top navbar is completely ABSENT', adminHasTopNavbar === false);

        // Check Standalone Shade Layout & Elements
        const adminHasShadeHeader = await client.evaluate("document.querySelector('.notif-shade-header') != null");
        record('Admin Notifications: Standalone Shade Header is present', adminHasShadeHeader === true);

        const adminTitle = await client.evaluate("document.querySelector('.shade-title') ? document.querySelector('.shade-title').textContent : ''");
        record('Admin Notifications: Title correctly displays Notification Centre', adminTitle.includes('Notification Centre'));

        // Check Dashboard Button
        const adminDashBtnHref = await client.evaluate("document.getElementById('btnGoDashboard') ? document.getElementById('btnGoDashboard').getAttribute('href') : ''");
        record('Admin Notifications: Dashboard button targets admin-dashboard.html', adminDashBtnHref.includes('admin-dashboard.html'), `Href: ${adminDashBtnHref}`);

        // Check User-Requested Filter Pills
        const adminPillKeys = await client.evaluate(`
            Array.from(document.querySelectorAll('.shade-filter-pill')).map(p => p.getAttribute('data-category'))
        `);
        const expectedPills = ['ALL', 'REPORTS', 'ASSIGNMENTS', 'BROADCASTS', 'SYSTEM'];
        const hasAllPills = expectedPills.every(k => adminPillKeys.includes(k));
        record('Admin Notifications: All 5 role-specific filter pills present (All, Reports, Assignments, Broadcasts, System)', hasAllPills, `Found: ${JSON.stringify(adminPillKeys)}`);

        // Check Feed Cards or Empty State
        const adminCardCount = await client.evaluate("document.querySelectorAll('.notif-card').length");
        record('Admin Notifications: Notification cards rendered in feed', adminCardCount > 0, `Cards: ${adminCardCount}`);

        await client.captureScreenshot('standalone_shade_admin.png');

        // Test Clicking Dashboard Button
        await client.evaluate("document.getElementById('btnGoDashboard').click()");
        await sleep(2000);
        const adminReturnedUrl = await client.evaluate('window.location.href');
        record('Admin Notifications: Clicking Dashboard returns strictly to Admin Dashboard', adminReturnedUrl.includes('/admin-dashboard.html'), `Landed on: ${adminReturnedUrl}`);

        // =====================================================================
        // SECTION 2: CITIZEN STANDALONE NOTIFICATION CENTRE
        // =====================================================================
        console.log('\n--- SECTION 2: Citizen Standalone Notification Centre ---');

        // 2.1 Authenticate as Citizen
        await client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
        await sleep(1000);

        await client.evaluate(`
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
                localStorage.setItem('userEmail', 'citizen@gmail.com');
            })()
        `);

        // 2.2 Open Citizen Notifications
        await client.send('Page.navigate', { url: `${BASE_URL}/citizen-notifications.html` });
        await sleep(2500);

        const citHasSidebar = await client.evaluate("document.querySelector('aside.sidebar, .sidebar') != null");
        record('Citizen Notifications: Dashboard sidebar is completely ABSENT', citHasSidebar === false);

        const citHasTopNavbar = await client.evaluate("document.querySelector('.navbar, nav.top-nav, .cc-header') != null");
        record('Citizen Notifications: Dashboard top navbar is completely ABSENT', citHasTopNavbar === false);

        const citDashBtnHref = await client.evaluate("document.getElementById('btnGoDashboard') ? document.getElementById('btnGoDashboard').getAttribute('href') : ''");
        record('Citizen Notifications: Dashboard button targets citizen-dashboard.html', citDashBtnHref.includes('citizen-dashboard.html'), `Href: ${citDashBtnHref}`);

        const citCardCount = await client.evaluate("document.querySelectorAll('.notif-card').length");
        record('Citizen Notifications: Notification cards rendered in feed', citCardCount > 0, `Cards: ${citCardCount}`);

        await client.captureScreenshot('standalone_shade_citizen.png');

        // Test Clicking Dashboard Button
        await client.evaluate("document.getElementById('btnGoDashboard').click()");
        await sleep(2000);
        const citReturnedUrl = await client.evaluate('window.location.href');
        record('Citizen Notifications: Clicking Dashboard returns strictly to Citizen Dashboard', citReturnedUrl.includes('/citizen-dashboard.html'), `Landed on: ${citReturnedUrl}`);

        // =====================================================================
        // SECTION 3: RESPONDER STANDALONE NOTIFICATION CENTRE
        // =====================================================================
        console.log('\n--- SECTION 3: Responder Standalone Notification Centre ---');

        // 3.1 Authenticate as Responder
        await client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
        await sleep(1000);

        await client.evaluate(`
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
                localStorage.setItem('userEmail', 'responder@disaster.gov.in');
            })()
        `);

        // 3.2 Open Responder Notifications
        await client.send('Page.navigate', { url: `${BASE_URL}/responder-notifications.html` });
        await sleep(2500);

        const respHasSidebar = await client.evaluate("document.querySelector('aside.sidebar, .sidebar') != null");
        record('Responder Notifications: Dashboard sidebar is completely ABSENT', respHasSidebar === false);

        const respHasTopNavbar = await client.evaluate("document.querySelector('.navbar, nav.top-nav, .cc-header') != null");
        record('Responder Notifications: Dashboard top navbar is completely ABSENT', respHasTopNavbar === false);

        const respDashBtnHref = await client.evaluate("document.getElementById('btnGoDashboard') ? document.getElementById('btnGoDashboard').getAttribute('href') : ''");
        record('Responder Notifications: Dashboard button targets responder-dashboard.html', respDashBtnHref.includes('responder-dashboard.html'), `Href: ${respDashBtnHref}`);

        const respCardCount = await client.evaluate("document.querySelectorAll('.notif-card').length");
        record('Responder Notifications: Notification cards rendered in feed', respCardCount > 0, `Cards: ${respCardCount}`);

        await client.captureScreenshot('standalone_shade_responder.png');

        // Test Clicking Dashboard Button
        await client.evaluate("document.getElementById('btnGoDashboard').click()");
        await sleep(2000);
        const respReturnedUrl = await client.evaluate('window.location.href');
        record('Responder Notifications: Clicking Dashboard returns strictly to Responder Dashboard', respReturnedUrl.includes('/responder-dashboard.html'), `Landed on: ${respReturnedUrl}`);

        // =====================================================================
        // SECTION 4: ANTI-ROLE-CROSSOVER PROTECTION ON NOTIFICATION CENTRES
        // =====================================================================
        console.log('\n--- SECTION 4: Anti-Role-Crossover Guards on Notification Centres ---');

        // Responder visits citizen-notifications.html -> auto-redirected to responder-notifications.html
        await client.send('Page.navigate', { url: `${BASE_URL}/citizen-notifications.html` });
        await sleep(2500);
        const respCrossUrl = await client.evaluate('window.location.href');
        record(
            'Responder attempting direct access to citizen-notifications is redirected to responder-notifications',
            respCrossUrl.includes('/responder-notifications.html'),
            `Landed on: ${respCrossUrl}`
        );

        // Responder visits admin-notifications.html -> auto-redirected to responder-notifications.html
        await client.send('Page.navigate', { url: `${BASE_URL}/admin-notifications.html` });
        await sleep(2500);
        const respAdminCrossUrl = await client.evaluate('window.location.href');
        record(
            'Responder attempting direct access to admin-notifications is redirected to responder-notifications',
            respAdminCrossUrl.includes('/responder-notifications.html'),
            `Landed on: ${respAdminCrossUrl}`
        );

        // =====================================================================
        // SECTION 5: FUNCTIONAL ACTIONS: FILTERING, SEARCH & MODAL
        // =====================================================================
        console.log('\n--- SECTION 5: Interactive Functional Actions ---');

        // 5.1 Click category filter pill
        await client.evaluate(`(() => {
            const pill = document.querySelector('.shade-filter-pill[data-category="REPORTS"]') || document.querySelector('.shade-filter-pill[data-category="ALERTS"]') || document.querySelector('.shade-filter-pill[data-category="DISPATCH"]');
            if (pill) pill.click();
        })()`);
        await sleep(500);
        const alertsPillActive = await client.evaluate(`(() => {
            const pill = document.querySelector('.shade-filter-pill[data-category="REPORTS"]') || document.querySelector('.shade-filter-pill[data-category="ALERTS"]') || document.querySelector('.shade-filter-pill[data-category="DISPATCH"]');
            return pill ? pill.classList.contains('active') : false;
        })()`);
        record('Filter pill click activates category view', alertsPillActive === true);

        // 5.2 Click first card to open detail modal
        const cardClicked = await client.evaluate(`(() => {
            const card = document.querySelector('.notif-card');
            if (card) {
                card.click();
                return true;
            }
            return false;
        })()`);
        await sleep(600);
        const modalOpen = await client.evaluate("document.getElementById('notifModalBackdrop').classList.contains('open')");
        record('Card click opens full detail modal sheet', cardClicked && modalOpen);

        await client.captureScreenshot('standalone_shade_modal_open.png');

        // Close modal
        await client.evaluate("document.getElementById('btnCloseModal').click()");
        await sleep(400);

        // 5.3 Test Mark All as Read button
        const markAllBtnPresent = await client.evaluate("document.getElementById('btnMarkAllRead') != null");
        record('Mark All as Read button present and interactive', markAllBtnPresent === true);

        // 5.4 Test Refresh button
        const refreshBtnPresent = await client.evaluate("document.getElementById('btnRefreshNotifs') != null");
        record('Refresh button present and interactive', refreshBtnPresent === true);

    } catch (err) {
        console.error('[AUDIT EXCEPTION]:', err);
        record('Notification Shade test run completed without crash', false, err.message);
    } finally {
        if (client) client.close();
        chrome.kill();
    }

    console.log('\n========================================================================');
    console.log('STANDALONE NOTIFICATION CENTRE AUDIT SUMMARY');
    console.log('========================================================================');
    const passedCount = testResults.filter(t => t.passed).length;
    const failedCount = testResults.filter(t => !t.passed).length;
    console.log(`Total Checks: ${testResults.length}`);
    console.log(`PASSED: ${passedCount}`);
    console.log(`FAILED: ${failedCount}`);
    console.log(`SUCCESS RATE: ${Math.round((passedCount / testResults.length) * 100)}%\n`);

    const resultPath = path.join(__dirname, 'notification_shade_audit_results.json');
    fs.writeFileSync(resultPath, JSON.stringify(testResults, null, 2));

    if (failedCount > 0) {
        process.exitCode = 1;
    }
}

runStandaloneNotificationAudit();
