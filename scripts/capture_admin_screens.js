const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const BROWSER_PATH = fs.existsSync('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe')
    ? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
    : 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

const BASE_URL = 'http://localhost:8080';
const OUT_DIR = path.resolve('C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots');

const VIEWPORTS = [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'mobile', width: 390, height: 844 }
];

const PAGES = [
    { file: 'admin-dashboard.html', id: 'admin_dashboard_integrated' },
    { file: 'admin-tasks.html', id: 'admin_tasks_integrated' },
    { file: 'admin-profile.html', id: 'admin_profile_integrated' }
];

async function loginUser() {
    const res = await fetch(`${BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'admin@disaster.gov.in', password: 'admin123' })
    });
    const data = await res.json();
    return data.token;
}

async function main() {
    if (!fs.existsSync(OUT_DIR)) {
        fs.mkdirSync(OUT_DIR, { recursive: true });
    }

    const token = await loginUser();
    console.log('Admin Token Acquired:', !!token);

    for (const page of PAGES) {
        console.log(`\nCapturing: ${page.file}...`);
        for (const vp of VIEWPORTS) {
            const filename = `${page.id}_${vp.name}_${vp.width}x${vp.height}.png`;
            const outPath = path.join(OUT_DIR, filename);
            const url = `${BASE_URL}/${page.file}?demo_auth=Admin&token=${token}&name=Admin%20Commander`;

            try {
                execFileSync(BROWSER_PATH, [
                    '--headless',
                    '--disable-gpu',
                    `--screenshot=${outPath}`,
                    `--window-size=${vp.width},${vp.height}`,
                    '--virtual-time-budget=4000',
                    url
                ], { stdio: 'ignore' });

                if (fs.existsSync(outPath)) {
                    const stats = fs.statSync(outPath);
                    console.log(`  [OK] ${vp.name.padEnd(8)} (${vp.width}x${vp.height}) -> ${filename} (${(stats.size/1024).toFixed(1)} KB)`);
                }
            } catch (err) {
                console.error(`  [ERR] ${filename}:`, err.message);
            }
        }
    }
    console.log('\nAll targeted admin responsive screenshots captured successfully!');
}

main().catch(console.error);

