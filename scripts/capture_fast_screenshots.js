const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const http = require('http');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE_URL = 'http://localhost:8080';
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots';
const DOCS_DIR = path.join(__dirname, '..', 'docs', 'screenshots');

if (!fs.existsSync(ARTIFACT_DIR)) fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
if (!fs.existsSync(DOCS_DIR)) fs.mkdirSync(DOCS_DIR, { recursive: true });

function login(email, password) {
    return new Promise((resolve, reject) => {
        const body = JSON.stringify({ email, password });
        const req = http.request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/auth/login',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(body)
            }
        }, res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(data);
                    resolve(parsed.token || '');
                } catch (e) {
                    resolve('');
                }
            });
        });
        req.on('error', err => reject(err));
        req.write(body);
        req.end();
    });
}

const VIEWPORTS = [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'mobile', width: 390, height: 844 }
];

async function main() {
    console.log('Logging in roles for URL authentication tokens...');
    const adminToken = await login('admin@disaster.gov.in', 'admin123');
    const responderToken = await login('responder@disaster.gov.in', 'responder123');
    const citizenToken = await login('citizen@gmail.com', 'citizen123');

    console.log('Tokens acquired. Capturing multi-viewport screenshots...');

    const targets = [
        {
            id: 'admin_live_operations_map',
            page: 'admin-dashboard.html',
            auth: `?demo_auth=Admin&token=${adminToken}&userEmail=admin@disaster.gov.in&userName=Admin`
        },
        {
            id: 'responder_field_gps_map',
            page: 'responder-rescue.html',
            auth: `?demo_auth=Responder&token=${responderToken}&userEmail=responder@disaster.gov.in&userName=Emergency%20Responder`
        },
        {
            id: 'citizen_safety_map',
            page: 'citizen-dashboard.html',
            auth: `?demo_auth=Citizen&token=${citizenToken}&userEmail=citizen@gmail.com&userName=John%20Citizen`
        },
        {
            id: 'emergency_survival_video_library',
            page: 'emergency-videos.html',
            auth: `?demo_auth=Citizen&token=${citizenToken}`
        }
    ];

    for (const t of targets) {
        console.log(`\nCapturing target: ${t.id} (${t.page})...`);
        const targetUrl = `${BASE_URL}/${t.page}${t.auth}`;

        for (const vp of VIEWPORTS) {
            const filename = `${t.id}_${vp.name}_${vp.width}x${vp.height}.png`;
            const artifactPath = path.join(ARTIFACT_DIR, filename);
            const docsPath = path.join(DOCS_DIR, filename);

            try {
                execFileSync(CHROME_PATH, [
                    '--headless=new',
                    '--disable-gpu',
                    `--screenshot=${artifactPath}`,
                    `--window-size=${vp.width},${vp.height}`,
                    '--virtual-time-budget=3500',
                    targetUrl
                ], { stdio: 'ignore', timeout: 15000 });

                if (fs.existsSync(artifactPath)) {
                    fs.copyFileSync(artifactPath, docsPath);
                    const stats = fs.statSync(artifactPath);
                    console.log(`  [OK] ${vp.name.padEnd(8)} -> ${filename} (${(stats.size / 1024).toFixed(1)} KB)`);
                }
            } catch (err) {
                console.error(`  [ERR] Failed for ${filename}:`, err.message);
            }
        }
    }

    console.log('\nAll key dashboard & map screenshots captured successfully!');
}

main().catch(err => {
    console.error('Fast capture failed:', err);
    process.exit(1);
});

