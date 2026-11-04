/**
 * Disaster Management Command System
 * Complete Multi-Device Viewport Screenshot Pipeline
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE_URL = 'http://localhost:8080';
const OUT_DIR = path.resolve(__dirname, '..', 'docs', 'screenshots');

const VIEWPORTS = [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'laptop', width: 1366, height: 768 },
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'mobile', width: 390, height: 844 }
];

const PAGES = [
    // Admin Suite
    { role: 'Admin', file: 'admin-dashboard.html', id: 'admin_dashboard' },
    { role: 'Admin', file: 'admin-alerts.html', id: 'admin_alerts' },
    { role: 'Admin', file: 'reports.html', id: 'admin_reports' },
    { role: 'Admin', file: 'admin-tasks.html', id: 'admin_tasks' },
    { role: 'Admin', file: 'admin-profile.html', id: 'admin_profile' },

    // Citizen Suite
    { role: 'Citizen', file: 'citizen-dashboard.html', id: 'citizen_dashboard' },
    { role: 'Citizen', file: 'profile.html', id: 'citizen_profile' },

    // Responder Suite
    { role: 'Responder', file: 'responder-dashboard.html', id: 'responder_dashboard' },
    { role: 'Responder', file: 'responder-rescue.html', id: 'responder_rescue' },
    { role: 'Responder', file: 'responder-tasks.html', id: 'responder_tasks' },
    { role: 'Responder', file: 'responder-profile.html', id: 'responder_profile' },

    // Gateway
    { role: null, file: 'index.html', id: 'portal_login' }
];

async function loginUser(email, password) {
    const res = await fetch(`${BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });
    const data = await res.json();
    return data.token;
}

async function main() {
    if (!fs.existsSync(OUT_DIR)) {
        fs.mkdirSync(OUT_DIR, { recursive: true });
    }

    console.log('Obtaining authentication tokens...');
    const tokens = {
        Admin: await loginUser('admin@disaster.gov.in', 'admin123'),
        Citizen: await loginUser('citizen@gmail.com', 'citizen123'),
        Responder: await loginUser('responder@disaster.gov.in', 'responder123')
    };

    console.log('Admin Token:', !!tokens.Admin);
    console.log('Citizen Token:', !!tokens.Citizen);
    console.log('Responder Token:', !!tokens.Responder);

    const manifest = [];

    for (const page of PAGES) {
        console.log(`\n===============================================================`);
        console.log(`PAGE: ${page.id} (${page.file}) [Role: ${page.role || 'Public'}]`);
        console.log(`===============================================================`);

        for (const vp of VIEWPORTS) {
            const filename = `${page.id}_${vp.name}_${vp.width}x${vp.height}.png`;
            const outPath = path.join(OUT_DIR, filename);

            let url = `${BASE_URL}/${page.file}`;
            if (page.role && tokens[page.role]) {
                url += `?demo_auth=${page.role}&token=${tokens[page.role]}&name=${encodeURIComponent(page.role + ' User')}`;
            }

            try {
                execFileSync(CHROME_PATH, [
                    '--headless=new',
                    '--disable-gpu',
                    `--screenshot=${outPath}`,
                    `--window-size=${vp.width},${vp.height}`,
                    '--virtual-time-budget=3500',
                    url
                ], { stdio: 'ignore' });

                if (fs.existsSync(outPath)) {
                    const stats = fs.statSync(outPath);
                    const kb = (stats.size / 1024).toFixed(1);
                    console.log(`  [OK] ${vp.name.padEnd(8)} (${vp.width}x${vp.height}) -> ${filename} (${kb} KB)`);
                    manifest.push({
                        page: page.file,
                        id: page.id,
                        role: page.role,
                        viewport: vp.name,
                        resolution: `${vp.width}x${vp.height}`,
                        filename: filename,
                        sizeBytes: stats.size
                    });
                } else {
                    console.error(`  [FAIL] Could not generate ${filename}`);
                }
            } catch (err) {
                console.error(`  [ERROR] ${filename}:`, err.message);
            }
        }
    }

    fs.writeFileSync(path.join(OUT_DIR, 'screenshots_manifest.json'), JSON.stringify(manifest, null, 2));
    console.log(`\n===============================================================`);
    console.log(`Completed all ${manifest.length} screenshots! Manifest saved to docs/screenshots/screenshots_manifest.json`);
    console.log(`===============================================================`);
}

main().catch(console.error);

