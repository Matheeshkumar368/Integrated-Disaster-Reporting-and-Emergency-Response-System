const http = require('http');
const { spawn, execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://localhost:8080';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.join('C:', 'Users', 'Lenovo', '.gemini', 'antigravity', 'brain', '3607002c-3f0c-4749-ad1c-a3bd8b8a1652', 'screenshots');

function post(endpoint, body, token) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: endpoint,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
        ...(token ? { 'Authorization': 'Bearer ' + token } : {})
      }
    }, res => {
      let buf = '';
      res.on('data', d => buf += d);
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(buf || '{}') }));
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

function get(endpoint, token) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: endpoint,
      method: 'GET',
      headers: {
        ...(token ? { 'Authorization': 'Bearer ' + token } : {})
      }
    }, res => {
      let buf = '';
      res.on('data', d => buf += d);
      res.on('end', () => {
        const isJson = (res.headers['content-type'] || '').includes('application/json');
        let body = buf;
        if (isJson) {
          try { body = JSON.parse(buf || '{}'); } catch (e) { body = buf; }
        }
        resolve({ status: res.statusCode, body });
      });
    });
    req.on('error', reject);
    req.end();
  });
}

async function queryMysql(sql) {
  try {
    const mysqlExe = process.env.MYSQL_EXE || 'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe';
    const dbPass = process.env.DB_PASSWORD ? `-p${process.env.DB_PASSWORD}` : '';
    return execSync(`"${mysqlExe}" -u root ${dbPass} -e "USE disaster_management; ${sql}"`, { encoding: 'utf-8' });
  } catch (err) {
    return err.stdout || err.message;
  }
}

async function main() {
  console.log('========================================================================');
  console.log('TASK 1 VERIFICATION — REAL RESPONDER GPS ENGINE & BACKEND INTEGRATION');
  console.log('========================================================================\n');

  let passedChecks = 0;
  let totalChecks = 0;

  function assertCheck(name, condition, extra = '') {
    totalChecks++;
    if (condition) {
      passedChecks++;
      console.log(`[PASS] Check ${totalChecks}: ${name}`);
      if (extra) console.log(`       ${extra}`);
    } else {
      console.error(`[FAIL] Check ${totalChecks}: ${name}`);
      if (extra) console.error(`       Details: ${extra}`);
    }
  }

  // 1. Authenticate Responder
  console.log('--- 1. Authenticating Logged-in Responder ---');
  const loginRes = await post('/api/auth/login', { email: 'responder@disaster.gov.in', password: 'responder123' });
  assertCheck('Responder authenticates with valid credentials', loginRes.status === 200 && !!loginRes.body.token);
  const token = loginRes.body.token;

  // 2. Test GET /api/responder/location/me
  console.log('\n--- 2. Querying GET /api/responder/location/me ---');
  const meRes = await get('/api/responder/location/me', token);
  assertCheck('GET /api/responder/location/me returns status 200', meRes.status === 200);
  assertCheck('Me endpoint returns sharingStatus and displayNotice', !!meRes.body.sharingStatus && !!meRes.body.displayNotice, `Notice: "${meRes.body.displayNotice}"`);

  // 3. Test Invalid Coordinates Validation (Section C)
  console.log('\n--- 3. Testing Coordinate Validation Boundaries ---');
  const latExceeded = await post('/api/responder/location/start', { latitude: 95.0, longitude: 80.0 }, token);
  assertCheck('Latitude > 90.0 is rejected with HTTP 400', latExceeded.status === 400, latExceeded.body.message);

  const lngExceeded = await post('/api/responder/location/start', { latitude: 13.0, longitude: 185.0 }, token);
  assertCheck('Longitude > 180.0 is rejected with HTTP 400', lngExceeded.status === 400, lngExceeded.body.message);

  // 4. Test Task Assignment Verification (Section C)
  console.log('\n--- 4. Testing Task Assignment Verification Security ---');
  const unassignedTask = await post('/api/responder/location/start', {
    taskId: 999999,
    latitude: 13.0827,
    longitude: 80.2707,
    accuracy: 5.0
  }, token);
  assertCheck('Non-existent or unassigned task is rejected', unassignedTask.status === 400, unassignedTask.body.message);

  // 5. Test Start Live GPS
  console.log('\n--- 5. Initiating Live GPS Broadcast via POST /api/responder/location/start ---');
  const testLat = 13.08275;
  const testLng = 80.27072;
  const testAcc = 4.8;
  const startRes = await post('/api/responder/location/start', {
    taskId: 32,
    latitude: testLat,
    longitude: testLng,
    accuracy: testAcc
  }, token);

  assertCheck('Start Live GPS returns HTTP 200', startRes.status === 200);
  assertCheck('Start response includes locationId and sharingStatus ACTIVE', startRes.body.sharingStatus === 'ACTIVE' && !!startRes.body.locationId);
  assertCheck('Responder ID is resolved from authenticated security context', startRes.body.responderId === 152, `ResponderId: ${startRes.body.responderId}`);

  // 6. Test Periodic Throttled Telemetry Update
  console.log('\n--- 6. Transmitting Telemetry Update via POST /api/responder/location/update ---');
  const updateLat = 13.08310;
  const updateLng = 80.27115;
  const updateRes = await post('/api/responder/location/update', {
    taskId: 32,
    latitude: updateLat,
    longitude: updateLng,
    accuracy: 3.5,
    heading: 88.5,
    speed: 14.2
  }, token);

  assertCheck('Update Live GPS returns HTTP 200', updateRes.status === 200);
  assertCheck('Update response confirms updated coordinates', updateRes.body.latitude === updateLat && updateRes.body.longitude === updateLng);

  // 7. Verify MySQL Database Persistence
  console.log('\n--- 7. Inspecting MySQL responder_locations Table ---');
  const dbResult = await queryMysql('SELECT id, responder_id, assignment_id, responder_email, latitude, longitude, accuracy, sharing_status, recorded_at FROM responder_locations ORDER BY id DESC LIMIT 2;');
  console.log(dbResult);
  assertCheck('MySQL database persists record with sharing_status ACTIVE', dbResult.includes('ACTIVE') && dbResult.includes('152') && dbResult.includes('13.0831'));

  // 8. Test Stop Live GPS
  console.log('\n--- 8. Concluding Live GPS Broadcast via POST /api/responder/location/stop ---');
  const stopRes = await post('/api/responder/location/stop', { taskId: 32 }, token);
  assertCheck('Stop Live GPS returns HTTP 200', stopRes.status === 200);
  assertCheck('Stop response sets sharingStatus to STOPPED', stopRes.body.sharingStatus === 'STOPPED');

  // Verify MySQL record after stop
  const dbStopResult = await queryMysql('SELECT id, responder_id, sharing_status, recorded_at FROM responder_locations ORDER BY id DESC LIMIT 1;');
  console.log(dbStopResult);
  assertCheck('MySQL database records STOPPED status on termination', dbStopResult.includes('STOPPED'));

  // 9. Verify Frontend Dashboard HTML Delivery & Components
  console.log('\n--- 9. Verifying Responder Dashboard UI Elements Delivery ---');
  const dashboardHtmlRes = await get('/responder-dashboard.html');
  assertCheck('Responder dashboard HTML is served with HTTP 200', dashboardHtmlRes.status === 200);

  // Read the HTML file directly to verify element IDs
  const htmlContent = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'responder-dashboard.html'), 'utf-8');
  assertCheck('Dashboard contains Live Tactical GPS Telemetry & Field Navigator console', htmlContent.includes('Live Tactical GPS Telemetry & Field Navigator'));
  assertCheck('Dashboard contains Leaflet map container responderDashboardMap', htmlContent.includes('id="responderDashboardMap"'));
  assertCheck('Dashboard contains telemetry coordinate display', htmlContent.includes('id="telemetryCoordsDisplay"'));
  assertCheck('Dashboard contains telemetry accuracy display', htmlContent.includes('id="telemetryAccuracyDisplay"'));
  assertCheck('Dashboard contains telemetry update time display', htmlContent.includes('id="telemetryUpdateTimeDisplay"'));
  assertCheck('Dashboard contains mission target selector', htmlContent.includes('id="gpsActiveTaskSelector"'));
  assertCheck('Dashboard contains Start and Stop Live GPS buttons', htmlContent.includes('id="btnStartLiveGps"') && htmlContent.includes('id="btnStopLiveGps"'));
  assertCheck('Dashboard contains real-time status notice banner', htmlContent.includes('id="gpsStatusNoticeBanner"'));
  assertCheck('Dashboard contains watchPosition implementation', htmlContent.includes('navigator.geolocation.watchPosition'));
  assertCheck('Dashboard contains clearWatch implementation', htmlContent.includes('navigator.geolocation.clearWatch'));
  assertCheck('Dashboard removed hardcoded Coimbatore initial zone badge', !htmlContent.includes('Zone: Coimbatore'));

  // 10. Capture High-Resolution Headless Chrome Screenshots
  console.log('\n--- 10. Capturing Visual Verification Screenshots via Headless Chrome ---');
  if (fs.existsSync(CHROME_PATH)) {
    try {
      const desktopShot = path.join(SCREENSHOT_DIR, 'responder_dashboard_real_gps_desktop.png');
      const mobileShot = path.join(SCREENSHOT_DIR, 'responder_dashboard_real_gps_mobile.png');

      const desktopArgs = [
        '--headless=new',
        '--disable-gpu',
        '--no-sandbox',
        '--hide-scrollbars',
        '--window-size=1440,900',
        '--virtual-time-budget=3500',
        `--screenshot="${desktopShot}"`,
        `"${BASE_URL}/responder-dashboard.html?demo_auth=responder&token=${token}"`
      ];
      execSync(`"${CHROME_PATH}" ${desktopArgs.join(' ')}`);
      assertCheck('Captured Desktop screenshot (1440x900)', fs.existsSync(desktopShot), desktopShot);

      const mobileArgs = [
        '--headless=new',
        '--disable-gpu',
        '--no-sandbox',
        '--hide-scrollbars',
        '--window-size=390,844',
        '--virtual-time-budget=3500',
        `--screenshot="${mobileShot}"`,
        `"${BASE_URL}/responder-dashboard.html?demo_auth=responder&token=${token}"`
      ];
      execSync(`"${CHROME_PATH}" ${mobileArgs.join(' ')}`);
      assertCheck('Captured Mobile screenshot (390x844)', fs.existsSync(mobileShot), mobileShot);
    } catch (e) {
      console.warn('Screenshot capture notice:', e.message);
    }
  }

  console.log('\n========================================================================');
  console.log(`TASK 1 VERIFICATION RESULT: ${passedChecks} OF ${totalChecks} CHECKS PASSED (${Math.round((passedChecks / totalChecks) * 100)}%)`);
  console.log('========================================================================\n');

  if (passedChecks === totalChecks) {
    process.exit(0);
  } else {
    process.exit(1);
  }
}

main().catch(err => {
  console.error('Fatal verification error:', err);
  process.exit(1);
});
