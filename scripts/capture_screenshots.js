/**
 * Disaster Management & Alert System
 * Automated Viewport Screenshot & Responsive Fidelity Pipeline
 * Uses Native Chrome DevTools Protocol (CDP) over WebSocket
 */

const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE_URL = 'http://localhost:8080';
const SCREENSHOT_DIR = path.join(__dirname, '..', 'docs', 'screenshots');
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots';

const VIEWPORTS = [
    { name: 'desktop', width: 1440, height: 900, mobile: false, scale: 1 },
    { name: 'laptop', width: 1366, height: 768, mobile: false, scale: 1 },
    { name: 'tablet', width: 768, height: 1024, mobile: true, scale: 1 },
    { name: 'mobile', width: 390, height: 844, mobile: true, scale: 1 }
];

const PAGES_CONFIG = [
    // Admin Pages
    { role: 'Admin', email: 'admin@disaster.gov.in', pass: 'admin123', page: 'admin-dashboard.html', id: 'admin_dashboard' },
    { role: 'Admin', email: 'admin@disaster.gov.in', pass: 'admin123', page: 'admin-alerts.html', id: 'admin_alerts' },
    { role: 'Admin', email: 'admin@disaster.gov.in', pass: 'admin123', page: 'reports.html', id: 'admin_reports' },
    { role: 'Admin', email: 'admin@disaster.gov.in', pass: 'admin123', page: 'admin-tasks.html', id: 'admin_tasks' },
    { role: 'Admin', email: 'admin@disaster.gov.in', pass: 'admin123', page: 'admin-profile.html', id: 'admin_profile' },
    
    // Citizen Pages
    { role: 'Citizen', email: 'citizen@gmail.com', pass: 'citizen123', page: 'citizen-dashboard.html', id: 'citizen_dashboard' },
    { role: 'Citizen', email: 'citizen@gmail.com', pass: 'citizen123', page: 'profile.html', id: 'citizen_profile' },
    
    // Responder Pages
    { role: 'Responder', email: 'responder@disaster.gov.in', pass: 'responder123', page: 'responder-dashboard.html', id: 'responder_dashboard' },
    { role: 'Responder', email: 'responder@disaster.gov.in', pass: 'responder123', page: 'responder-rescue.html', id: 'responder_rescue' },
    { role: 'Responder', email: 'responder@disaster.gov.in', pass: 'responder123', page: 'responder-tasks.html', id: 'responder_tasks' },
    { role: 'Responder', email: 'responder@disaster.gov.in', pass: 'responder123', page: 'responder-profile.html', id: 'responder_profile' },

    // Portal Gateway & Survival Videos
    { role: null, email: null, pass: null, page: 'emergency-videos.html', id: 'emergency_videos_library' },
    { role: null, email: null, pass: null, page: 'index.html', id: 'portal_login' }
];

async function loginUser(email, password) {
    if (!email) return { token: '', role: '', userName: '' };
    try {
        const res = await fetch(`${BASE_URL}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        return {
            token: data.token || '',
            role: data.role || '',
            userName: data.name || (email.split('@')[0])
        };
    } catch (e) {
        console.error(`Login error for ${email}:`, e.message);
        return { token: '', role: '', userName: '' };
    }
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
    if (!fs.existsSync(SCREENSHOT_DIR)) {
        fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    }
    if (!fs.existsSync(ARTIFACT_DIR)) {
        fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
    }

    console.log('===============================================================');
    console.log('  DISASTER MANAGEMENT — MULTI-DEVICE VIEWPORT SCREENSHOT ENGINE ');
    console.log('===============================================================');

    // Pre-authenticate all required roles
    console.log('Fetching JWT tokens for Admin, Citizen, Responder...');
    const tokens = {
        Admin: await loginUser('admin@disaster.gov.in', 'admin123'),
        Citizen: await loginUser('citizen@gmail.com', 'citizen123'),
        Responder: await loginUser('responder@disaster.gov.in', 'responder123')
    };

    console.log('Admin Token:', !!tokens.Admin.token);
    console.log('Citizen Token:', !!tokens.Citizen.token);
    console.log('Responder Token:', !!tokens.Responder.token);

    const port = 9225;
    const tempDir = path.join('C:\\Users\\Lenovo\\AppData\\Local\\Temp', `chrome_ss_${Date.now()}`);

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
        console.log('Connected to Chrome DevTools Protocol!');

        await client.send('Page.enable');
        await client.send('DOM.enable');
        await client.send('CSS.enable');

        const results = [];

        for (const item of PAGES_CONFIG) {
            console.log(`\n--- Capturing: ${item.id} (${item.page}) ---`);
            const auth = item.role ? tokens[item.role] : { token: '', role: '', userName: '' };

            for (const vp of VIEWPORTS) {
                const filename = `${item.id}_${vp.name}_${vp.width}x${vp.height}.png`;
                const filepath = path.join(SCREENSHOT_DIR, filename);

                // Configure Viewport
                await client.send('Emulation.setDeviceMetricsOverride', {
                    width: vp.width,
                    height: vp.height,
                    deviceScaleFactor: vp.scale,
                    mobile: vp.mobile
                });

                if (vp.mobile) {
                    await client.send('Emulation.setTouchEmulationEnabled', { enabled: true });
                } else {
                    await client.send('Emulation.setTouchEmulationEnabled', { enabled: false });
                }

                // Navigate to index to seed tokens if authenticated
                if (auth.token) {
                    await client.send('Page.navigate', { url: `${BASE_URL}/index.html` });
                    await new Promise(r => setTimeout(r, 400));
                    await client.send('Runtime.evaluate', {
                        expression: `
                            localStorage.setItem('token', '${auth.token}');
                            localStorage.setItem('userRole', '${auth.role}');
                            localStorage.setItem('userName', '${auth.userName}');
                            localStorage.setItem('userEmail', '${item.email}');
                        `
                    });
                } else {
                    await client.send('Runtime.evaluate', {
                        expression: `localStorage.clear();`
                    });
                }

                // Navigate to target page
                await client.send('Page.navigate', { url: `${BASE_URL}/${item.page}` });

                // Allow Leaflet maps, Chart.js, and API polling to settle
                await new Promise(r => setTimeout(r, 2500));

                // Capture Screenshot
                const shot = await client.send('Page.captureScreenshot', {
                    format: 'png',
                    captureBeyondViewport: false
                });

                const buf = Buffer.from(shot.data, 'base64');
                fs.writeFileSync(filepath, buf);
                try { fs.writeFileSync(path.join(ARTIFACT_DIR, filename), buf); } catch (e) {}
                const kb = (buf.length / 1024).toFixed(1);

                console.log(`  [OK] ${vp.name.padEnd(8)} (${vp.width}x${vp.height}) -> ${filename} (${kb} KB)`);
                results.push({
                    page: item.page,
                    id: item.id,
                    viewport: vp.name,
                    dimensions: `${vp.width}x${vp.height}`,
                    filename,
                    sizeBytes: buf.length
                });
            }
        }

        console.log(`\n===============================================================`);
        console.log(` Successfully captured ${results.length} multi-viewport screenshots!`);
        console.log(` Stored in: ${SCREENSHOT_DIR}`);
        console.log(`===============================================================`);

        // Save metadata index
        fs.writeFileSync(
            path.join(SCREENSHOT_DIR, 'screenshots_manifest.json'),
            JSON.stringify(results, null, 2)
        );

        client.ws.close();
    } catch (err) {
        console.error('Screenshot execution error:', err);
    } finally {
        chromeProc.kill();
    }
}

run();

