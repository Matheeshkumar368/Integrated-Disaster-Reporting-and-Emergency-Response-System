/**
 * capture_citizen_screens.js
 * Captures dedicated screenshots for Citizen Safety Hub across viewports and tabs
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

const CITIZEN_SCREENS = [
    {
        id: 'citizen_dashboard_overview',
        file: 'citizen-dashboard.html',
        query: 'tab=alerts',
        title: 'Citizen Dashboard - Real-Time Disaster Warnings & Telemetry HUD'
    },
    {
        id: 'citizen_emergency_report',
        file: 'citizen-dashboard.html',
        query: 'tab=report',
        title: 'Citizen Safety Hub - Dedicated Incident Reporting Form'
    },
    {
        id: 'citizen_my_reports_tracker',
        file: 'citizen-dashboard.html',
        query: 'tab=my-reports',
        title: 'Citizen Safety Hub - My Incident History & 4-Stage Stepper'
    },
    {
        id: 'citizen_safety_guidelines',
        file: 'citizen-dashboard.html',
        query: 'tab=safety',
        title: 'Citizen Safety Hub - National Disaster Preparedness Guidelines'
    },
    {
        id: 'citizen_profile_view',
        file: 'profile.html',
        query: '',
        title: 'Citizen Civilian Profile & Emergency Contact Credentials'
    }
];

async function loginCitizen() {
    const res = await fetch(`${BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'citizen@gmail.com', password: 'citizen123' })
    });
    const data = await res.json();
    return data.token;
}

async function run() {
    if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
    if (!fs.existsSync(ARTIFACT_DIR)) fs.mkdirSync(ARTIFACT_DIR, { recursive: true });

    console.log('Authenticating Citizen session...');
    const citizenToken = await loginCitizen();
    console.log('Citizen token acquired successfully.');

    for (const screen of CITIZEN_SCREENS) {
        console.log(`\n===============================================================`);
        console.log(`SCREEN: ${screen.id} - ${screen.title}`);
        console.log(`===============================================================`);

        for (const vp of VIEWPORTS) {
            const filename = `${screen.id}_${vp.name}_${vp.width}x${vp.height}.png`;
            const outPath = path.join(OUT_DIR, filename);
            const artifactPath = path.join(ARTIFACT_DIR, filename);

            let url = `${BASE_URL}/${screen.file}?demo_auth=Citizen&token=${citizenToken}&name=${encodeURIComponent('John Citizen')}`;
            if (screen.query) {
                url += `&${screen.query}`;
            }

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
                    // Copy to artifact directory
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

    console.log('\nAll Citizen viewports captured and synced to artifacts directory!');
}

run().catch(console.error);

