/**
 * ==============================================================================
 * FINAL MASTER VERIFICATION TEST SUITE
 * Disaster Management & Alert System
 * 
 * Tests:
 * 1. Health & 3-Role Authentication (Admin, Responder, Citizen)
 * 2. Emergency Survival Video Library (10 categories, filtering, search, recommendations, CRUD)
 * 3. Admin Data Hygiene & Deduplication API
 * 4. Citizen Distress Reporting with Exact Coordinates & Live Emergency GPS Sharing
 * 5. Cross-Role Lifecycle: Citizen SOS -> Task Assignment -> Responder GPS Tracking -> ETA -> Resolution Sync
 * 6. Frontend Artifact Integrity (HTML pages, nav links, Leaflet integration)
 * ==============================================================================
 */

const http = require('http');

const BASE_URL = 'http://localhost:8080';
let adminToken = '';
let responderToken = '';
let citizenToken = '';

let testReportId = null;
let testTaskId = null;
let testVideoId = null;

let passedTests = 0;
let totalTests = 0;

function assert(condition, message) {
    totalTests++;
    if (condition) {
        passedTests++;
        console.log(`  [PASS] Test ${totalTests}: ${message}`);
    } else {
        console.error(`  [FAIL] Test ${totalTests}: ${message}`);
        throw new Error(`Assertion failed: ${message}`);
    }
}

function request(method, path, body = null, token = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(path, BASE_URL);
        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: method,
            headers: {
                'Accept': 'application/json'
            }
        };

        if (token) {
            options.headers['Authorization'] = `Bearer ${token}`;
        }

        let bodyData = null;
        if (body) {
            bodyData = typeof body === 'string' ? body : JSON.stringify(body);
            options.headers['Content-Type'] = 'application/json';
            options.headers['Content-Length'] = Buffer.byteLength(bodyData);
        }

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(data);
                } catch (e) {
                    parsed = data;
                }
                resolve({ statusCode: res.statusCode, headers: res.headers, body: parsed });
            });
        });

        req.on('error', err => reject(err));
        if (bodyData) req.write(bodyData);
        req.end();
    });
}

async function waitForServer(maxAttempts = 15) {
    console.log('--- Checking Spring Boot Server Readiness ---');
    for (let i = 1; i <= maxAttempts; i++) {
        try {
            const res = await request('GET', '/api/official-alerts/active');
            if (res.statusCode === 200) {
                console.log(`Server healthy and ready on port 8080 (attempt ${i})`);
                return true;
            }
        } catch (e) {
            process.stdout.write(`Waiting for backend boot... (${i}/${maxAttempts})\r`);
        }
        await new Promise(r => setTimeout(r, 2000));
    }
    throw new Error('Server did not become healthy within timeout.');
}

async function runTestSuite() {
    console.log('================================================================');
    console.log('EXECUTING COMPREHENSIVE FINAL MASTER VERIFICATION SUITE');
    console.log('================================================================\n');

    await waitForServer();

    // -------------------------------------------------------------------------
    // SECTION 1: AUTHENTICATION ACROSS ALL THREE ROLES
    // -------------------------------------------------------------------------
    console.log('\n[1/6] Authenticating All Three Roles...');
    
    // Admin Login
    const adminRes = await request('POST', '/api/auth/login', {
        email: 'admin@disaster.gov.in',
        password: 'admin123'
    });
    assert(adminRes.statusCode === 200 && adminRes.body.token, 'Admin authenticated and JWT received');
    adminToken = adminRes.body.token;

    // Responder Login
    const responderRes = await request('POST', '/api/auth/login', {
        email: 'responder@disaster.gov.in',
        password: 'responder123'
    });
    assert(responderRes.statusCode === 200 && responderRes.body.token, 'Responder authenticated and JWT received');
    responderToken = responderRes.body.token;

    // Citizen Login
    const citizenRes = await request('POST', '/api/auth/login', {
        email: 'citizen@gmail.com',
        password: 'citizen123'
    });
    assert(citizenRes.statusCode === 200 && citizenRes.body.token, 'Citizen authenticated and JWT received');
    citizenToken = citizenRes.body.token;

    // -------------------------------------------------------------------------
    // SECTION 2: EMERGENCY SURVIVAL VIDEO LIBRARY (PART 5)
    // -------------------------------------------------------------------------
    console.log('\n[2/6] Verifying Emergency Survival Video Library API...');

    // 1. Fetch all active videos
    const allVideosRes = await request('GET', '/api/emergency-videos');
    assert(allVideosRes.statusCode === 200 && Array.isArray(allVideosRes.body) && allVideosRes.body.length >= 10,
        `Seeded Survival Videos available (${allVideosRes.body.length} records returned)`);

    // 2. Filter by Category
    const floodVideosRes = await request('GET', '/api/emergency-videos?category=Flood');
    assert(floodVideosRes.statusCode === 200 && floodVideosRes.body.length > 0 &&
           floodVideosRes.body.every(v => v.category.toLowerCase().includes('flood')),
           'Category filter successfully isolates Flood videos');

    // 3. Filter by Language
    const tamilVideosRes = await request('GET', '/api/emergency-videos?language=Tamil');
    assert(tamilVideosRes.statusCode === 200 && tamilVideosRes.body.length > 0 &&
           tamilVideosRes.body.every(v => v.language.toLowerCase().includes('tamil')),
           'Language filter successfully isolates Tamil videos');

    // 4. Keyword Search
    const searchRes = await request('GET', '/api/emergency-videos?search=CPR');
    assert(searchRes.statusCode === 200 && searchRes.body.length > 0 &&
           searchRes.body.some(v => v.title.includes('CPR') || v.description.includes('CPR')),
           'Search query successfully returns First Aid & CPR protocols');

    // 5. Active Hazard Contextual Recommendation
    const recRes = await request('GET', '/api/emergency-videos/recommended?hazardType=Flood');
    assert(recRes.statusCode === 200 && recRes.body.length > 0 && recRes.body[0].category.toLowerCase().includes('flood'),
           'Contextual recommendation successfully suggests Flood survival video');

    // 6. Admin Video Creation
    const createVidRes = await request('POST', '/api/emergency-videos', {
        title: 'Master Test Tsunami High Ground Protocol',
        youtubeId: 'x0prflzsm38',
        category: 'Tsunami',
        sourceOrganization: 'INCOIS & NDMA',
        language: 'English',
        duration: '3:30',
        description: 'Test protocol for immediate coastal run to high ground.',
        alternativeGuide: 'Step 1: Run to 30m elevation.\nStep 2: Never return for belongings.'
    }, adminToken);
    assert(createVidRes.statusCode === 201 && createVidRes.body.id, 'Admin successfully created new emergency video');
    testVideoId = createVidRes.body.id;

    // 7. Admin Video Update
    const updateVidRes = await request('PUT', `/api/emergency-videos/${testVideoId}`, {
        duration: '4:00'
    }, adminToken);
    assert(updateVidRes.statusCode === 200 && updateVidRes.body.duration === '4:00',
           'Admin successfully updated emergency video metadata');

    // 8. Admin Video Deactivate/Delete
    const delVidRes = await request('DELETE', `/api/emergency-videos/${testVideoId}`, null, adminToken);
    assert(delVidRes.statusCode === 200, 'Admin successfully deactivated test emergency video');

    // -------------------------------------------------------------------------
    // SECTION 3: ADMIN DATA HYGIENE & DEDUPLICATION (PART 6)
    // -------------------------------------------------------------------------
    console.log('\n[3/6] Verifying Admin Data Hygiene & Deduplication API...');

    // 1. Get Hygiene Stats
    const hygieneStatsRes = await request('GET', '/api/admin/data/hygiene-stats', null, adminToken);
    assert(hygieneStatsRes.statusCode === 200 && hygieneStatsRes.body.totalReports !== undefined,
           `Hygiene stats retrieved: Total Reports=${hygieneStatsRes.body.totalReports}, Duplicates=${hygieneStatsRes.body.duplicateReportsDetected}`);

    // 2. Clean duplicate submissions
    const cleanDedupeRes = await request('POST', '/api/admin/data/clean-duplicates', {}, adminToken);
    assert(cleanDedupeRes.statusCode === 200 && cleanDedupeRes.body.status === 'success',
           `Data hygiene run executed successfully (${cleanDedupeRes.body.duplicatesCleaned} duplicates archived)`);

    // -------------------------------------------------------------------------
    // SECTION 4: CITIZEN DISTRESS REPORT WITH GPS & LIVE TRACKING (PARTS 1 & 4)
    // -------------------------------------------------------------------------
    console.log('\n[4/6] Verifying Citizen Distress Report GPS & Live Telemetry Beacon...');

    // 1. Create Report with Exact Coordinates
    const createRepRes = await request('POST', '/api/citizen-reports', {
        location: 'Anna Salai, Chennai',
        message: 'Master Test Emergency: Water rising past entrance gates.',
        incidentType: 'Flood',
        latitude: 13.0604,
        longitude: 80.2496,
        accuracy: 12.5,
        severity: 'Critical'
    }, citizenToken);
    assert(createRepRes.statusCode === 200 && createRepRes.body.reportId,
           `Citizen distress report created with exact GPS coordinates (#${createRepRes.body.reportId})`);
    testReportId = createRepRes.body.reportId;
    assert(createRepRes.body.latitude === 13.0604 && createRepRes.body.longitude === 80.2496,
           'Distress report coordinates persisted accurately in database');

    // 2. Citizen Starts Live Location Sharing Beacon
    const startLiveRes = await request('POST', `/api/citizen-reports/${testReportId}/live-location/start`, {
        latitude: 13.0605,
        longitude: 80.2497,
        accuracy: 8.0
    }, citizenToken);
    assert(startLiveRes.statusCode === 200 && startLiveRes.body.status === 'success',
           'Citizen successfully activated live emergency GPS sharing');

    // 3. Citizen Transmits Updated Beacon
    const updateLiveRes = await request('POST', `/api/citizen-reports/${testReportId}/live-location/update`, {
        latitude: 13.0610,
        longitude: 80.2500,
        accuracy: 6.5
    }, citizenToken);
    assert(updateLiveRes.statusCode === 200 && updateLiveRes.body.report.liveLatitude === 13.0610,
           'Citizen live emergency location coordinates updated');

    // 4. Admin Checks Active Live Citizen Distress Beacons
    const activeLiveRes = await request('GET', '/api/citizen-reports/active-live', null, adminToken);
    assert(activeLiveRes.statusCode === 200 && Array.isArray(activeLiveRes.body) &&
           activeLiveRes.body.some(r => r.reportId === testReportId),
           'Admin Radar endpoint successfully detects live citizen distress beacon');

    // 5. Citizen Stops Live Location Sharing
    const stopLiveRes = await request('POST', `/api/citizen-reports/${testReportId}/live-location/stop`, {}, citizenToken);
    assert(stopLiveRes.statusCode === 200 && stopLiveRes.body.report.liveTrackingEnabled === false,
           'Citizen successfully terminated live emergency GPS sharing');

    // -------------------------------------------------------------------------
    // SECTION 5: CROSS-ROLE WORKFLOW & LIVE RESPONDER TELEMETRY (PARTS 1, 2, 3, 4)
    // -------------------------------------------------------------------------
    console.log('\n[5/6] Verifying Cross-Role Dispatch, Responder Live GPS & Resolution Sync...');

    // 1. Admin creates RescueTask linked to the Citizen Report
    const createTaskRes = await request('POST', '/api/tasks', {
        title: 'Flash Flood Rescue Mission - Anna Salai',
        description: 'Deploy rubberized raft to evacuate stranded civilians.',
        zone: 'Chennai',
        priority: 'High',
        assignedTo: 'responder@disaster.gov.in',
        assignedResponderName: 'Emergency Responder',
        locationName: 'Anna Salai, Chennai',
        latitude: 13.0604,
        longitude: 80.2496,
        citizenReportId: testReportId
    }, adminToken);
    const createdTaskObj = createTaskRes.body.task || createTaskRes.body;
    assert(createTaskRes.statusCode === 200 && (createdTaskObj.taskId || createdTaskObj.id),
           `Admin created Rescue Task #${createdTaskObj.taskId || createdTaskObj.id} linked to Citizen Report #${testReportId}`);
    testTaskId = createdTaskObj.taskId || createdTaskObj.id;

    // 2. Responder explicitly acknowledges task
    const ackRes = await request('PUT', `/api/tasks/${testTaskId}/acknowledge`, {}, responderToken);
    const ackTask = ackRes.body.task || ackRes.body;
    assert(ackRes.statusCode === 200 && (ackTask.status === 'Acknowledged' || ackRes.body.status === 'success'),
           'Responder acknowledged mission task');

    // 3. Verify cross-role synchronization: Citizen report is now Acknowledged
    const checkCitizenRepRes = await request('GET', '/api/citizen-reports/my-reports', null, citizenToken);
    const linkedRep = checkCitizenRepRes.body.find(r => r.reportId === testReportId);
    assert(linkedRep && linkedRep.status === 'Acknowledged',
           'Cross-role synchronization: Citizen Report status transitioned to Acknowledged');

    // 4. Responder starts live GPS tracking
    const startTrackingRes = await request('POST', '/api/tracking/start', {
        assignmentId: testTaskId,
        latitude: 13.0700,
        longitude: 80.2600,
        accuracy: 10.0
    }, responderToken);
    assert(startTrackingRes.statusCode === 200 && startTrackingRes.body.status === 'success',
           'Responder started live GPS tracking session');

    // 5. Responder updates telemetry (moving along route)
    const updateTrackingRes = await request('POST', '/api/tracking/update', {
        assignmentId: testTaskId,
        latitude: 13.0650,
        longitude: 80.2550,
        accuracy: 8.0,
        heading: 215.0,
        speed: 12.5
    }, responderToken);
    assert(updateTrackingRes.statusCode === 200 && updateTrackingRes.body.latitude === 13.0650,
           'Responder live GPS coordinate update recorded');

    // 6. Admin checks Active Radar Units
    const adminActiveTrackingRes = await request('GET', '/api/tracking/active', null, adminToken);
    assert(adminActiveTrackingRes.statusCode === 200 &&
           adminActiveTrackingRes.body.some(s => s.assignmentId === testTaskId),
           'Admin Live Operations Map radar detects responder active GPS movement');

    // 7. Citizen checks assigned responder live telemetry via citizenReportId
    const citizenCheckTrackingRes = await request('GET', `/api/tracking/latest/${testReportId}`, null, citizenToken);
    assert(citizenCheckTrackingRes.statusCode === 200 && citizenCheckTrackingRes.body.latitude === 13.0650,
           'Citizen Safety Map successfully queries live telemetry of assigned responder by reportId');
    assert(citizenCheckTrackingRes.body.isStale === false, 'Telemetry freshness check confirms live signal');

    // 8. Responder completes mission with completion summary
    const resolveTaskRes = await request('POST', `/api/tasks/${testTaskId}/resolve`, {
        summary: 'Civilian family of 4 safely evacuated to high ground relief center. Medical check passed.'
    }, responderToken);
    const resolvedTask = resolveTaskRes.body.task || resolveTaskRes.body;
    assert(resolveTaskRes.statusCode === 200 && (resolvedTask.status === 'Completed' || resolveTaskRes.body.status === 'success'),
           'Responder completed and resolved field rescue mission');

    // 9. Verify cross-role synchronization: Citizen report is now Resolved
    const verifyResolvedRepRes = await request('GET', '/api/citizen-reports/my-reports', null, citizenToken);
    const resolvedRep = verifyResolvedRepRes.body.find(r => r.reportId === testReportId);
    assert(resolvedRep && resolvedRep.status === 'Resolved',
           'Cross-role synchronization: Citizen Report status automatically synchronized to Resolved upon task completion');

    // 10. Responder stops tracking session
    const stopTrackingRes = await request('POST', '/api/tracking/stop', {
        assignmentId: testTaskId
    }, responderToken);
    assert(stopTrackingRes.statusCode === 200, 'Responder tracking session concluded on mission completion');

    // -------------------------------------------------------------------------
    // SECTION 6: FRONTEND STATIC RESOURCE DELIVERY & INTEGRITY
    // -------------------------------------------------------------------------
    console.log('\n[6/6] Verifying Frontend Static Delivery & Navigation Links...');

    const videoHtmlRes = await request('GET', '/emergency-videos.html');
    assert(videoHtmlRes.statusCode === 200 && typeof videoHtmlRes.body === 'string' &&
           videoHtmlRes.body.includes('EMERGENCY SURVIVAL VIDEO LIBRARY'),
           'emergency-videos.html renders successfully with official protocol headers');

    const adminHtmlRes = await request('GET', '/admin-dashboard.html');
    assert(adminHtmlRes.statusCode === 200 && adminHtmlRes.body.includes('Survival Videos') &&
           adminHtmlRes.body.includes('Data Hygiene'),
           'admin-dashboard.html contains Survival Videos navigation and Data Hygiene controls');

    const responderHtmlRes = await request('GET', '/responder-rescue.html');
    assert(responderHtmlRes.statusCode === 200 && responderHtmlRes.body.includes('responderFieldMap') &&
           responderHtmlRes.body.includes('Survival Videos'),
           'responder-rescue.html contains Field GPS navigation map and Survival Videos navigation');

    const citizenHtmlRes = await request('GET', '/citizen-dashboard.html');
    assert(citizenHtmlRes.statusCode === 200 && citizenHtmlRes.body.includes('Survival Videos') &&
           citizenHtmlRes.body.includes('btnCitizenLiveShare'),
           'citizen-dashboard.html contains Survival Videos navigation and Live GPS sharing controls');

    console.log('\n================================================================');
    console.log(`FINAL RESULT: ${passedTests} OF ${totalTests} VERIFICATION CHECKS PASSED (100%)`);
    console.log('================================================================\n');
}

runTestSuite().catch(err => {
    console.error('\nVerification Suite Failed:', err.message);
    process.exit(1);
});
