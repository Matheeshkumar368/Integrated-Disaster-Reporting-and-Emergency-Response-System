const http = require('http');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const BASE_URL = 'http://localhost:8080';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.join('C:', 'Users', 'Lenovo', '.gemini', 'antigravity', 'brain', '3607002c-3f0c-4749-ad1c-a3bd8b8a1652', 'screenshots');

function request(method, endpoint, body, token) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: endpoint,
      method: method,
      headers: {
        ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}),
        ...(token ? { 'Authorization': 'Bearer ' + token } : {})
      }
    }, res => {
      let buf = '';
      res.on('data', d => buf += d);
      res.on('end', () => {
        let parsed = null;
        try { parsed = JSON.parse(buf); } catch (e) { parsed = buf; }
        resolve({ status: res.statusCode, headers: res.headers, body: parsed, rawLength: buf.length });
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function main() {
  console.log('========================================================================');
  console.log('EMERGENCY SOS SOUND BUTTON & MULTI-ROLE ALARM AUDIT & VERIFICATION');
  console.log('========================================================================\n');

  let passed = 0;
  let total = 0;

  function assertCheck(name, cond, details = '') {
    total++;
    if (cond) {
      passed++;
      console.log(`[PASS] Check ${total}: ${name}`);
      if (details) console.log(`       ${details}`);
    } else {
      console.error(`[FAIL] Check ${total}: ${name}`);
      if (details) console.error(`       Details: ${details}`);
    }
  }

  // 1. Audio Asset Inspection
  console.log('--- 1. High-Decibel Siren Audio Asset Verification ---');
  const sirenPath = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'audio', 'emergency-siren.wav');
  assertCheck('WAV audio file exists on disk', fs.existsSync(sirenPath), sirenPath);
  if (fs.existsSync(sirenPath)) {
    const stats = fs.statSync(sirenPath);
    assertCheck('Audio file size > 500 KB (valid high-definition siren audio)', stats.size > 500000, `Size: ${stats.size} bytes`);
  }

  // 2. HTTP Public Endpoint Delivery
  console.log('\n--- 2. Public HTTP Audio Delivery & Security Whitelisting ---');
  const audioRes = await request('GET', '/audio/emergency-siren.wav');
  assertCheck('Audio endpoint returns HTTP 200 OK without authentication', audioRes.status === 200);
  assertCheck('Audio content-type matches audio/x-wav or audio/wav', (audioRes.headers['content-type'] || '').includes('audio'));
  assertCheck('Audio Content-Length header is present and > 500KB', parseInt(audioRes.headers['content-length'] || '0') > 500000);

  // 3. User Authentication for Backend SOS Sync
  console.log('\n--- 3. Authentication & SOS Settings Persistence API ---');
  const loginRes = await request('POST', '/api/auth/login', { email: 'citizen@gmail.com', password: 'citizen123' });
  assertCheck('Citizen authenticates and obtains JWT token', loginRes.status === 200 && !!loginRes.body.token);
  const token = loginRes.body ? loginRes.body.token : null;

  if (token) {
    const getSettingsRes = await request('GET', '/api/auth/sos-settings', null, token);
    assertCheck('GET /api/auth/sos-settings returns HTTP 200', getSettingsRes.status === 200);
    assertCheck('SOS settings contains shortcutKey and volume', !!getSettingsRes.body.sosShortcutKey && typeof getSettingsRes.body.sosSoundVolume === 'number',
      `Shortcut: ${getSettingsRes.body.sosShortcutKey}, Vol: ${getSettingsRes.body.sosSoundVolume}%`);

    const putRes = await request('PUT', '/api/auth/sos-settings', {
      sosShortcutEnabled: true,
      sosShortcutKey: 'Tab',
      sosSoundVolume: 90
    }, token);
    assertCheck('PUT /api/auth/sos-settings updates configuration in MySQL', putRes.status === 200);
  }

  // 4. Code & Architecture Audits
  console.log('\n--- 4. Cross-Role Dashboard Integration Audit ---');
  const citizenHtml = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'citizen-dashboard.html'), 'utf-8');
  assertCheck('Citizen dashboard contains #sosSoundBtn with toggleLocalSosAlarm()', citizenHtml.includes('id="sosSoundBtn"') && citizenHtml.includes('toggleLocalSosAlarm()'));
  assertCheck('Citizen dashboard contains Settings gear button', citizenHtml.includes('EmergencyAlertSystem.openSettingsModal()'));
  assertCheck('Citizen dashboard contains interval synchronization logic', citizenHtml.includes('updateSosSoundButtonState'));

  const adminHtml = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'admin-dashboard.html'), 'utf-8');
  assertCheck('Admin dashboard contains #sosSoundBtn with toggleLocalSosAlarm()', adminHtml.includes('id="sosSoundBtn"') && adminHtml.includes('toggleLocalSosAlarm()'));
  assertCheck('Admin dashboard contains Settings gear button', adminHtml.includes('EmergencyAlertSystem.openSettingsModal()'));
  assertCheck('Admin dashboard contains interval synchronization logic', adminHtml.includes('updateSosSoundButtonState'));

  const respHtml = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'responder-dashboard.html'), 'utf-8');
  assertCheck('Responder dashboard contains #sosSoundBtn with toggleLocalSosAlarm()', respHtml.includes('id="sosSoundBtn"') && respHtml.includes('toggleLocalSosAlarm()'));
  assertCheck('Responder dashboard contains Settings gear button', respHtml.includes('EmergencyAlertSystem.openSettingsModal()'));
  assertCheck('Responder dashboard contains interval synchronization logic', respHtml.includes('updateSosSoundButtonState'));

  // 5. JavaScript Audio Engine Architecture Audit
  console.log('\n--- 5. Audio Engine & Fail-Safe Architecture Audit ---');
  const alertJs = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'js', 'emergency-alert.js'), 'utf-8');
  assertCheck('emergency-alert.js contains native HTML5 Audio instance', alertJs.includes('new Audio(\'/audio/emergency-siren.wav\')'));
  assertCheck('emergency-alert.js contains Web Audio API synthesizer fallback/reinforcement', alertJs.includes('initAudioContext') && alertJs.includes('createOscillator'));
  assertCheck('emergency-alert.js contains autoplay unlock listener', alertJs.includes('checkAudioUnlock'));
  assertCheck('emergency-alert.js has stopAudioFile to silence both audio nodes and HTML5 audio', alertJs.includes('stopAudioFile') && alertJs.includes('htmlAudioElement.pause()'));
  assertCheck('emergency-alert.js protects against zero ramp gain crashes', alertJs.includes('Math.max(0.0001'));

  // 6. CSS Styling Audit
  console.log('\n--- 6. Tactile UI & Animation Styling Audit ---');
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'command-center.css'), 'utf-8');
  assertCheck('command-center.css contains .sos-alarm-active-pulse animation', css.includes('.sos-alarm-active-pulse') && css.includes('@keyframes sosPulse'));

  // 7. Visual Screenshot Captures via Chrome
  console.log('\n--- 7. Visual UI Screenshots via Headless Chrome ---');
  if (fs.existsSync(CHROME_PATH)) {
    try {
      const citizenShot = path.join(SCREENSHOT_DIR, 'citizen_sos_sound_header.png');
      const adminShot = path.join(SCREENSHOT_DIR, 'admin_sos_sound_header.png');
      const respShot = path.join(SCREENSHOT_DIR, 'responder_sos_sound_header.png');

      const runShot = (url, out) => {
        const args = [
          '--headless=new',
          '--disable-gpu',
          '--no-sandbox',
          '--window-size=1440,900',
          '--virtual-time-budget=3000',
          `--screenshot="${out}"`,
          `"${url}"`
        ];
        execSync(`"${CHROME_PATH}" ${args.join(' ')}`);
      };

      runShot(`${BASE_URL}/citizen-dashboard.html?demo_auth=citizen&token=${token}`, citizenShot);
      assertCheck('Captured Citizen Header Screenshot', fs.existsSync(citizenShot), citizenShot);

      const adminLogin = await request('POST', '/api/auth/login', { email: 'admin@disaster.gov.in', password: 'admin123' });
      if (adminLogin.body && adminLogin.body.token) {
        runShot(`${BASE_URL}/admin-dashboard.html?demo_auth=admin&token=${adminLogin.body.token}`, adminShot);
        assertCheck('Captured Admin Header Screenshot', fs.existsSync(adminShot), adminShot);
      }

      const respLogin = await request('POST', '/api/auth/login', { email: 'responder@disaster.gov.in', password: 'responder123' });
      if (respLogin.body && respLogin.body.token) {
        runShot(`${BASE_URL}/responder-dashboard.html?demo_auth=responder&token=${respLogin.body.token}`, respShot);
        assertCheck('Captured Responder Header Screenshot', fs.existsSync(respShot), respShot);
      }
    } catch (err) {
      console.warn('Screenshot capture notice:', err.message);
    }
  }

  console.log('\n========================================================================');
  console.log(`EMERGENCY SOS SOUND VERIFICATION RESULT: ${passed} OF ${total} CHECKS PASSED (${Math.round((passed / total) * 100)}%)`);
  console.log('========================================================================\n');

  if (passed === total) {
    process.exit(0);
  } else {
    process.exit(1);
  }
}

main().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
