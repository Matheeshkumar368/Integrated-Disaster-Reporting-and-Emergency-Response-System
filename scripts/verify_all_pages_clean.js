const { spawn } = require('child_process');
const http = require('http');
const path = require('path');
const fs = require('fs');

const BASE_URL = 'http://localhost:8080';
const CDP_PORT = 9223;

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}

function httpGet(url) {
    return new Promise((resolve, reject) => {
        http.get(url, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try { resolve(JSON.parse(data)); } catch(e) { resolve(data); }
            });
        }).on('error', reject);
    });
}

class CdpClient {
    constructor(wsUrl) {
        this.wsUrl = wsUrl;
        this.ws = null;
        this.id = 1;
        this.callbacks = new Map();
        this.consoleErrors = [];
    }

    connect() {
        return new Promise((resolve, reject) => {
            this.ws = new globalThis.WebSocket(this.wsUrl);
            this.ws.onopen = () => resolve();
            this.ws.onerror = (err) => reject(err);
            this.ws.onmessage = (event) => {
                const msg = JSON.parse(event.data);
                if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
                    this.consoleErrors.push(msg.params.args.map(a => a.value || a.description).join(' '));
                }
                if (msg.id && this.callbacks.has(msg.id)) {
                    const { resolve, reject } = this.callbacks.get(msg.id);
                    this.callbacks.delete(msg.id);
                    if (msg.error) reject(msg.error);
                    else resolve(msg.result);
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
            throw new Error(res.exceptionDetails.text || JSON.stringify(res.exceptionDetails));
        }
        return res.result ? res.result.value : undefined;
    }

    close() {
        if (this.ws) {
            try { this.ws.close(); } catch(e) {}
        }
    }
}

async function verifyAllPages() {
    console.log('========================================================================');
    console.log('VERIFY ALL CORE PAGES: CLEANUP, ALIGNMENT & DATA AUDIT');
    console.log('========================================================================');

    const tempDir = path.join(__dirname, '..', 'scratch', 'chrome_audit_profile');
    if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });

    const chrome = spawn(CHROME_PATH, [
        '--headless=new',
        `--remote-debugging-port=${CDP_PORT}`,
        `--user-data-dir=${tempDir}`,
        '--disable-gpu',
        '--window-size=1280,800',
        'about:blank'
    ]);

    let client = null;
    const results = [];

    try {
        await sleep(1500);
        let targets = await httpGet(`http://localhost:${CDP_PORT}/json`);
        const pageTarget = targets.find(t => t.type === 'page') || targets[0];
        client = new CdpClient(pageTarget.webSocketDebuggerUrl);
        await client.connect();

        await client.send('Page.enable');
        await client.send('Runtime.enable');

        // Helper to login as role
        async function loginAs(email, password, expectedRole) {
            await client.send('Page.navigate', { url: `${BASE_URL}/index-new-login.html` });
            await sleep(800);
            await client.evaluate(`
                (async () => {
                    const res = await fetch('/api/auth/login', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ email: '${email}', password: '${password}' })
                    });
                    const d = await res.json();
                    localStorage.setItem('token', d.token);
                    localStorage.setItem('userRole', d.role);
                    localStorage.setItem('userName', d.name);
                    localStorage.setItem('userEmail', '${email}');
                })()
            `);
            await sleep(500);
        }

        const pagesToTest = [
            // Admin Pages
            { url: '/admin-dashboard.html', role: 'Admin', auth: ['admin@disaster.gov.in', 'admin123'], title: 'Mission Control' },
            { url: '/admin-notifications.html', role: 'Admin', auth: ['admin@disaster.gov.in', 'admin123'], title: 'Notification Centre' },
            { url: '/admin-users.html', role: 'Admin', auth: ['admin@disaster.gov.in', 'admin123'], title: 'User Management' },
            { url: '/reports.html', role: 'Admin', auth: ['admin@disaster.gov.in', 'admin123'], title: 'Telemetry' },
            { url: '/admin-alerts.html', role: 'Admin', auth: ['admin@disaster.gov.in', 'admin123'], title: 'Emergency Broadcast' },
            { url: '/admin-tasks.html', role: 'Admin', auth: ['admin@disaster.gov.in', 'admin123'], title: 'Responder Tasks' },
            { url: '/admin-profile.html', role: 'Admin', auth: ['admin@disaster.gov.in', 'admin123'], title: 'Security' },

            // Citizen Pages
            { url: '/citizen-dashboard.html', role: 'Citizen', auth: ['citizen@gmail.com', 'citizen123'], title: 'Citizen Safety' },
            { url: '/citizen-notifications.html', role: 'Citizen', auth: ['citizen@gmail.com', 'citizen123'], title: 'Notification Centre' },
            { url: '/emergency-videos.html', role: 'Citizen', auth: ['citizen@gmail.com', 'citizen123'], title: 'Survival' },
            { url: '/profile.html', role: 'Citizen', auth: ['citizen@gmail.com', 'citizen123'], title: 'Profile' },

            // Responder Pages
            { url: '/responder-dashboard.html', role: 'Responder', auth: ['responder@disaster.gov.in', 'responder123'], title: 'Tactical' },
            { url: '/responder-notifications.html', role: 'Responder', auth: ['responder@disaster.gov.in', 'responder123'], title: 'Notification Centre' },
            { url: '/responder-tasks.html', role: 'Responder', auth: ['responder@disaster.gov.in', 'responder123'], title: 'Tasks' },
            { url: '/responder-rescue.html', role: 'Responder', auth: ['responder@disaster.gov.in', 'responder123'], title: 'Operations' },
            { url: '/responder-profile.html', role: 'Responder', auth: ['responder@disaster.gov.in', 'responder123'], title: 'Profile' }
        ];

        let currentRole = null;
        for (const p of pagesToTest) {
            if (currentRole !== p.role) {
                await loginAs(p.auth[0], p.auth[1], p.role);
                currentRole = p.role;
            }

            client.consoleErrors = [];
            await client.send('Page.navigate', { url: `${BASE_URL}${p.url}` });
            await sleep(1500);

            const currentUrl = await client.evaluate('window.location.href');
            const docTitle = await client.evaluate('document.title');
            const bodyText = await client.evaluate('document.body.innerText');

            const passedUrl = !currentUrl.includes('index.html') && !currentUrl.includes('error');
            const hasDeadData = /speed:\s*42\s*km\/h|2,400\s*citizens|dummy/i.test(bodyText);

            const passed = passedUrl && !hasDeadData;
            results.push({
                page: p.url,
                role: p.role,
                passed,
                url: currentUrl,
                title: docTitle,
                hasDeadData,
                errors: client.consoleErrors
            });

            console.log(`[${passed ? 'PASS' : 'FAIL'}] [${p.role}] ${p.url} -> Title: "${docTitle.slice(0, 30)}" | No Fake Data: ${!hasDeadData}`);
        }

    } catch(err) {
        console.error('Audit exception:', err);
    } finally {
        if (client) client.close();
        chrome.kill();
    }

    console.log('\n========================================================================');
    console.log(`TOTAL PAGES TESTED: ${results.length}`);
    const passed = results.filter(r => r.passed).length;
    console.log(`PASSED: ${passed}/${results.length} (${Math.round((passed / results.length) * 100)}%)`);
    console.log('========================================================================');
}

verifyAllPages();
