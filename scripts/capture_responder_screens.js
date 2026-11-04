/**
 * capture_responder_screens.js
 * Captures dedicated screenshots for Responder Field Operations Command Center across viewports
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE_URL = 'http://localhost:8080';
const OUT_DIR = path.resolve(__dirname, '..', 'docs', 'screenshots');
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots';

const VIEWPORTS = [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'laptop', width: 1366, height: 768 },
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'mobile', width: 390, height: 844 }
];

const RESPONDER_SCREENS = [
    {
        id: 'responder_dashboard_queue',
        file: 'responder-dashboard.html',
        title: 'Responder Dashboard - Tactical Incident Queue & Telemetry HUD'
    },
    {
        id: 'responder_assigned_tasks',
        file: 'responder-tasks.html',
        title: 'Responder Field Operations - Assigned Rescue Tasks & Workflow Controls'
    },
    {
        id: 'responder_live_rescue_map',
        file: 'responder-rescue.html',
        title: 'Responder Operations - Live Rescue Operations & Citizen SOS'
    },
    {
        id: 'responder_unit_profile',
        file: 'responder-profile.html',
        title: 'Responder Profile - Field Unit Credentials & Zone Directives'
    }
];

async function loginResponder() {
    const res = await fetch(`${BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'responder@disaster.gov.in', password: 'responder123' })
    });
    const data = await res.json();
    return data.token;
}

async function run() {
    if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
    if (!fs.existsSync(ARTIFACT_DIR)) fs.mkdirSync(ARTIFACT_DIR, { recursive: true });

    console.log('Authenticating Responder session...');
    const token = await loginResponder();
    console.log('Responder token acquired successfully.');

    for (const screen of RESPONDER_SCREENS) {
        console.log(`\n===============================================================`);
        console.log(`SCREEN: ${screen.id} - ${screen.title}`);
        console.log(`===============================================================`);

        for (const vp of VIEWPORTS) {
            const filename = `${screen.id}_${vp.name}_${vp.width}x${vp.height}.png`;
            const outPath = path.join(OUT_DIR, filename);
            const artifactPath = path.join(ARTIFACT_DIR, filename);

            let url = `${BASE_URL}/${screen.file}?demo_auth=Responder&token=${token}&name=${encodeURIComponent('Emergency Responder')}`;

            try {
                execFileSync(CHROME_PATH, [
                    '--headless=new',
                    '--disable-gpu',
                    `--screenshot=${outPath}`,
                    `--window-size=${vp.width},${vp.height}`,
                    '--virtual-time-budget=4000',
                    url
                ], { stdio: 'ignore' });

                if (fs.existsSync(outPath)) {
                    fs.copyFileSync(outPath, artifactPath);
                    const stats = fs.statSync(outPath);
                    const kb = (stats.size / 1024).toFixed(1);
                    console.log(`  [OK] ${vp.name.padEnd(8)} (${vp.width}x${vp.height}) -> ${filename} (${kb} KB)`);
                } else {
                    console.error(`  [FAIL] Failed to generate ${filename}`);
                }
            } catch (e) {
                console.error(`  [ERROR] ${filename}:`, e.message);
            }
        }
    }

    console.log('\nAll Responder viewports captured and synced to artifacts directory!');
}

run().catch(console.error);

