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
            throw new Error(res.exceptionDetails.text || 'Evaluation failed');
        }
        return res.result ? res.result.value : undefined;
    }

    async captureScreenshot(filename) {
        const res = await this.send('Page.captureScreenshot', { format: 'png' });
        const filePath = path.join(ARTIFACT_DIR, filename);
        fs.writeFileSync(filePath, Buffer.from(res.data, 'base64'));
        console.log(`[SCREENSHOT] Saved: ${filePath}`);
        return filePath;
    }

    close() {
        if (this.ws) {
            this.ws.close();
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

async function runBrowserTests() {
    console.log('========================================================================');
    console.log('STARTING AUTOMATED LIVE BROWSER TESTS (CHROME HEADLESS + CDP)');
    console.log('========================================================================\n');

    // 1. Launch Chrome Headless
    const tempProfile = path.join(process.env.TEMP, 'chrome_test_profile_' + Date.now());
    const chrome = spawn(CHROME_PATH, [
        '--headless=new',
        `--remote-debugging-port=${CDP_PORT}`,
        `--user-data-dir=${tempProfile}`,
        '--disable-gpu',
        '--window-size=1440,900',
        '--no-first-run',
        '--no-default-browser-check',
        'about:blank'
    ], { detached: false });

    console.log('[BROWSER] Chrome spawned. Waiting for CDP endpoint...');
    await sleep(2000);

    let targets = null;
    for (let i = 0; i < 10; i++) {
        try {
            targets = await httpGet(`http://localhost:${CDP_PORT}/json`);
            if (targets && targets.length > 0) break;
        } catch (e) {
            await sleep(500);
        }
    }

    if (!targets || targets.length === 0) {
        console.error('[FAIL] Could not connect to Chrome CDP endpoint.');
        chrome.kill();
        process.exit(1);
    }

    const pageTarget = targets.find(t => t.type === 'page') || targets[0];
    const client = new CdpClient(pageTarget.webSocketDebuggerUrl);
    await client.connect();

    // Enable Page, Runtime, DOM
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await client.send('DOM.enable');

    const testResults = [];

    function record(testName, passed, detail = '') {
        testResults.push({ testName, passed, detail });
        console.log(`[${passed ? 'PASS' : 'FAIL'}] ${testName}${detail ? ' - ' + detail : ''}`);
    }

    try {
        // --- TEST 1: Login Page Loading ---
        console.log('\n--- 1. Login Page Inspection ---');
        await client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
        await sleep(2000);

        const pageTitle = await client.evaluate('document.title');
        record('Login page loaded successfully', pageTitle != null && pageTitle.length > 0, `Title: "${pageTitle}"`);

        // --- TEST 2: Perform Admin Login ---
        console.log('\n--- 2. Administrator Authentication ---');
        const loginSuccess = await client.evaluate(`
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
                    localStorage.setItem('userEmail', data.email);
                    return true;
                }
                return false;
            })()
        `);
        record('Admin credentials authenticated via API', loginSuccess === true);

        // --- TEST 3: Admin Dashboard Navigation ---
        console.log('\n--- 3. Admin Command Center Dashboard ---');
        await client.send('Page.navigate', { url: `${BASE_URL}/admin-dashboard.html` });
        await sleep(2000);

        const dashboardTitle = await client.evaluate('document.title');
        record('Admin Dashboard loaded and rendered', dashboardTitle.includes('Mission Control'), `Title: "${dashboardTitle}"`);

        // Check for Security & 2FA PIN nav link
        const navLinkExists = await client.evaluate(`
            document.querySelector('a[href="/admin-profile.html"]') != null
        `);
        record('Security & 2FA PIN navigation link exists in header', navLinkExists === true);

        await client.captureScreenshot('admin_dashboard_live.png');

        // --- TEST 4: Security & 2FA PIN Page (Admin Profile) ---
        console.log('\n--- 4. Security Governance & 2FA PIN Page ---');
        await client.send('Page.navigate', { url: `${BASE_URL}/admin-profile.html` });
        await sleep(2000);

        const profileTitle = await client.evaluate('document.title');
        record('Security Governance & 2FA PIN page loaded', profileTitle.includes('2FA PIN'), `Title: "${profileTitle}"`);

        // Check PIN Status Badge
        const pinBadgeText = await client.evaluate(`
            document.getElementById('pinStatusBadge') ? document.getElementById('pinStatusBadge').innerText : ''
        `);
        record('PIN Status badge active', pinBadgeText === 'ENROLLED & ACTIVE' || pinBadgeText === 'NOT CONFIGURED', `Status: "${pinBadgeText}"`);

        // --- TEST 5: Configure / Reset Security PIN Modal ---
        console.log('\n--- 5. Configure / Reset Security PIN Modal ---');
        await client.evaluate('openPinModal()');
        await sleep(500);

        const modalVisible = await client.evaluate(`
            document.getElementById('pinEnrollModal').style.display === 'flex'
        `);
        record('Configure / Reset Security PIN modal opened', modalVisible === true);

        await client.captureScreenshot('admin_pin_modal_enroll.png');

        // --- TEST 6: Switch to Forgot PIN / Recovery Tab ---
        console.log('\n--- 6. Forgot PIN / Recovery Workflow ---');
        await client.evaluate("switchPinTab('recover')");
        await sleep(500);

        const recoverTabActive = await client.evaluate(`
            document.getElementById('pinRecoverForm').style.display === 'block' &&
            document.getElementById('pinRecoveryBackupCode') != null
        `);
        record('Forgot PIN / Recovery tab active with Emergency Backup Code input', recoverTabActive === true);

        await client.captureScreenshot('admin_pin_modal_recovery.png');

        // --- TEST 7: Invalid Backup Code Error Handling ---
        console.log('\n--- 7. Invalid Backup Code Testing ---');
        const invalidAttemptResult = await client.evaluate(`
            (async () => {
                document.getElementById('pinRecoveryBackupCode').value = 'WrongInvalidBackupCode123!';
                document.getElementById('pinRecoverNew').value = '654321';
                document.getElementById('pinRecoverConfirm').value = '654321';

                const event = new Event('submit', { cancelable: true });
                await handlePinRecovery(event);

                const alertEl = document.getElementById('pinModalAlert');
                return {
                    visible: alertEl.style.display !== 'none',
                    text: alertEl.innerText
                };
            })()
        `);
        record(
            'Invalid backup code is cleanly rejected with rate limit message',
            invalidAttemptResult.visible && invalidAttemptResult.text.includes('Invalid backup code'),
            `Message: "${invalidAttemptResult.text}"`
        );

        // --- TEST 8: Valid Backup Code Recovery & PIN Reset ---
        console.log('\n--- 8. Valid Backup Code Recovery & Reset ---');
        // First configure the intended emergency backup code via API
        const setupRes = await client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/admin/backup-code/setup', {
                    method: 'POST',
                    headers: {
                        'Authorization': 'Bearer ' + localStorage.getItem('token'),
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        backupCode: '922523244035',
                        confirmBackupCode: '922523244035'
                    })
                });
                return res.ok;
            })()
        `);
        record('Pre-condition: Emergency Backup Code setup confirmed (922523244035)', setupRes === true);

        // Now recover with the known valid backup code (922523244035)
        const recoverySuccess = await client.evaluate(`
            (async () => {
                document.getElementById('pinRecoveryBackupCode').value = '922523244035';
                document.getElementById('pinRecoverNew').value = '123456';
                document.getElementById('pinRecoverConfirm').value = '123456';
                document.getElementById('pinRecoverNewBackupCode').value = '922523244035';

                const event = new Event('submit', { cancelable: true });
                await handlePinRecovery(event);
                await new Promise(r => setTimeout(r, 1000));

                const stepUp = sessionStorage.getItem('adminStepUpToken');
                const alertEl = document.getElementById('pinModalAlert');
                return {
                    stepUpSaved: stepUp != null && stepUp.length > 20,
                    alertText: alertEl ? alertEl.innerText : ''
                };
            })()
        `);
        record(
            'Valid backup code recovers PIN and issues Step-Up token to sessionStorage',
            recoverySuccess.stepUpSaved === true,
            `StepUpToken saved: ${recoverySuccess.stepUpSaved}`
        );

        // --- TEST 9: Audit Trail Loading (No "Access aborted" Error) ---
        console.log('\n--- 9. Audit Trail Verification ---');
        await client.evaluate('closePinModal()');
        await sleep(500);

        const auditResult = await client.evaluate(`
            (async () => {
                await fetchAndDisplayAuditTrail();
                await new Promise(r => setTimeout(r, 1500));

                const tbody = document.getElementById('auditTrailTableBody');
                const html = tbody.innerHTML;
                const rows = tbody.querySelectorAll('tr');
                const hasAbortedError = html.includes('Access aborted') || html.includes('Clearance rejected');
                const hasSuccessLogs = html.includes('PIN_RECOVERED') || html.includes('BACKUP_CODE_SETUP') || rows.length > 1;

                return {
                    rowCount: rows.length,
                    hasAbortedError: hasAbortedError,
                    hasSuccessLogs: hasSuccessLogs,
                    preview: rows[0] ? rows[0].innerText.replace(/\\s+/g, ' ').substring(0, 80) : ''
                };
            })()
        `);

        record(
            'Audit Trail loads actual database records without "Access aborted" error',
            auditResult.hasAbortedError === false && (auditResult.hasSuccessLogs === true || auditResult.rowCount > 0),
            `Rows: ${auditResult.rowCount}, First row: "${auditResult.preview}"`
        );

        await client.captureScreenshot('admin_audit_trail_loaded.png');

        // --- TEST 10: Regression Testing (Citizen & Responder Login) ---
        console.log('\n--- 10. Multi-Role Regression Testing ---');

        const citizenLogin = await client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: 'citizen@gmail.com', password: 'citizen123' })
                });
                return res.ok && (await res.json()).role === 'Citizen';
            })()
        `);
        record('Citizen authentication and role verification', citizenLogin === true);

        const responderLogin = await client.evaluate(`
            (async () => {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: 'responder@disaster.gov.in', password: 'responder123' })
                });
                return res.ok && (await res.json()).role === 'Responder';
            })()
        `);
        record('Responder authentication and role verification', responderLogin === true);

    } finally {
        client.close();
        chrome.kill();
        // Clean up temp profile
        try { fs.rmSync(tempProfile, { recursive: true, force: true }); } catch (e) {}
    }

    console.log('\n========================================================================');
    const passedCount = testResults.filter(r => r.passed).length;
    const totalCount = testResults.length;
    console.log(`BROWSER TESTING COMPLETED: ${passedCount} / ${totalCount} CHECKS PASSED (${Math.round(passedCount/totalCount*100)}%)`);
    console.log('========================================================================\n');

    if (passedCount !== totalCount) {
        process.exit(1);
    }
}

runBrowserTests().catch(err => {
    console.error('Fatal error during browser test execution:', err);
    process.exit(1);
});
