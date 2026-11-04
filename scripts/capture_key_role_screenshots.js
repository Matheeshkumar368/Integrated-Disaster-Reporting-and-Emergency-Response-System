const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE_URL = 'http://localhost:8080';
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots';

const VIEWPORTS = [
    { name: 'desktop', width: 1440, height: 900, mobile: false },
    { name: 'mobile', width: 390, height: 844, mobile: true }
];

const PAGES = [
    { role: 'Admin', email: 'admin@disaster.gov.in', pass: 'admin123', page: 'admin-dashboard.html', id: 'admin_dashboard_radar' },
    { role: 'Responder', email: 'responder@disaster.gov.in', pass: 'responder123', page: 'responder-tasks.html', id: 'responder_tasks_integrated' },
    { role: 'Responder', email: 'responder@disaster.gov.in', pass: 'responder123', page: 'responder-rescue.html', id: 'responder_rescue_integrated' },
    { role: 'Citizen', email: 'citizen@gmail.com', pass: 'citizen123', page: 'citizen-dashboard.html', id: 'citizen_dashboard_integrated' },
    { role: 'Citizen', email: 'citizen@gmail.com', pass: 'citizen123', page: 'emergency-videos.html', id: 'emergency_videos_library' }
];

async function loginUser(email, password) {
    const res = await fetch(`${BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });
    const data = await res.json();
    return {
        token: data.token,
        role: data.role,
        userName: data.name || email.split('@')[0]
    };
}

function createCdpClient(wsUrl) {
    const ws = new WebSocket(wsUrl);
    let id = 1;
    const callbacks = new Map();

    ws.addEventListener('message', (evt) => {
        const msg = JSON.parse(evt.data);
        if (msg.id && callbacks.has(msg.id)) {
            const cb = callbacks.get(msg.id);
            callbacks.delete(msg.id);
            if (msg.error) cb.reject(new Error(msg.error.message));
            else cb.resolve(msg.result);
        }
    });

    const send = (method, params = {}) => new Promise((resolve, reject) => {
        const msgId = id++;
        callbacks.set(msgId, { resolve, reject });
        ws.send(JSON.stringify({ id: msgId, method, params }));
    });

    const waitOpen = new Promise((resolve) => ws.addEventListener('open', resolve));
    return { ws, send, waitOpen };
}

async function run() {
    if (!fs.existsSync(ARTIFACT_DIR)) fs.mkdirSync(ARTIFACT_DIR, { recursive: true });

    console.log('Logging in test accounts...');
    const auth = {
        Admin: await loginUser('admin@disaster.gov.in', 'admin123'),
        Responder: await loginUser('responder@disaster.gov.in', 'responder123'),
        Citizen: await loginUser('citizen@gmail.com', 'citizen123')
    };

    const port = 9227;
    const tempDir = path.join('C:\\Users\\Lenovo\\AppData\\Local\\Temp', `chrome_cdp_${Date.now()}`);

    const chromeProc = spawn(CHROME_PATH, [
        '--headless=new',
        '--disable-gpu',
        `--remote-debugging-port=${port}`,
        `--user-data-dir=${tempDir}`,
        '--no-first-run',
        '--no-default-browser-check',
        'about:blank'
    ]);

    await new Promise(r => setTimeout(r, 2000));

    try {
        const listRes = await fetch(`http://127.0.0.1:${port}/json/list`);
        const pages = await listRes.json();
        const client = createCdpClient(pages[0].webSocketDebuggerUrl);
        await client.waitOpen;

        await client.send('Page.enable');
        await client.send('DOM.enable');
        await client.send('Runtime.enable');

        for (const item of PAGES) {
            console.log(`\nCapturing ${item.page} as ${item.role}...`);
            const userAuth = auth[item.role];

            // 1. Navigate to index.html first to set localStorage
            await client.send('Page.navigate', { url: `${BASE_URL}/index.html` });
            await new Promise(r => setTimeout(r, 500));

            const evalExpr = `
                localStorage.setItem('token', '${userAuth.token}');
                localStorage.setItem('userRole', '${userAuth.role}');
                localStorage.setItem('userEmail', '${item.email}');
                localStorage.setItem('userName', '${userAuth.userName}');
            `;
            await client.send('Runtime.evaluate', { expression: evalExpr });

            for (const vp of VIEWPORTS) {
                // Configure viewport
                await client.send('Emulation.setDeviceMetricsOverride', {
                    width: vp.width,
                    height: vp.height,
                    deviceScaleFactor: 1,
                    mobile: vp.mobile
                });

                // Navigate to target page
                await client.send('Page.navigate', { url: `${BASE_URL}/${item.page}` });
                await new Promise(r => setTimeout(r, 2500));

                const screenshot = await client.send('Page.captureScreenshot', { format: 'png' });
                const filename = `${item.id}_${vp.name}_${vp.width}x${vp.height}.png`;
                const outPath = path.join(ARTIFACT_DIR, filename);
                fs.writeFileSync(outPath, Buffer.from(screenshot.data, 'base64'));
                console.log(`  [OK] Captured: ${filename}`);
            }
        }

        client.ws.close();
    } catch (e) {
        console.error('CDP Error:', e);
    } finally {
        chromeProc.kill('SIGTERM');
        console.log('\nFinished capturing role screenshots.');
    }
}

run();
