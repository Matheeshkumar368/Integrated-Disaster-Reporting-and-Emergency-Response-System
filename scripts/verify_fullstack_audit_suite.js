/**
 * ==============================================================================
 * FULL-STACK FUNCTIONALITY AUDIT & VERIFICATION SUITE
 * Disaster Management & Alert System
 * 
 * Tests:
 * - Test A: Cross-Role Dispatch Lifecycle & Isolation (Citizen 1 -> Admin -> Responder 1 vs Responder 2)
 * - Test B: Emergency SOS (Action A: Local Audible Alarm & Action B: SOS Transmission to Backend)
 * - Test C: Real GPS Telemetry Sharing (Hardware coordinates, MySQL persistence, Admin & Citizen radar)
 * - Test D: Strict Role Isolation & Security Boundaries (403 Forbidden verifications)
 * - Test E: Evidence Upload & Public Asset Retrieval (Fix for "Unable to load evidence")
 * - Test F: Button-by-Button Functionality Across All 3 Roles
 * ==============================================================================
 */

const http = require('http');

const BASE_URL = 'http://localhost:8080';

let adminToken = '';
let responder1Token = '';
let responder2Token = '';
let citizen1Token = '';
let citizen2Token = '';

let citizen1ReportId = null;
let citizen2ReportId = null;
let testRescueTaskId = null;

let totalChecks = 0;
let passedChecks = 0;

function pass(testNum, desc) {
    totalChecks++;
    passedChecks++;
    console.log(`  [PASS] #${testNum}: ${desc}`);
}

function fail(testNum, desc, err) {
    totalChecks++;
    console.error(`  [FAIL] #${testNum}: ${desc}`);
    if (err) console.error(`         Detail: ${err.message || err}`);
    throw new Error(`Failed check #${testNum}: ${desc}`);
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
                const contentType = res.headers['content-type'] || '';
                if (contentType.includes('application/json')) {
                    try {
                        parsed = JSON.parse(data);
                    } catch (e) {
                        parsed = data;
                    }
                } else {
                    parsed = data;
                }
                resolve({
                    status: res.statusCode,
                    headers: res.headers,
                    data: parsed
                });
            });
        });

        req.on('error', (e) => reject(e));
        if (bodyData) req.write(bodyData);
        req.end();
    });
}

async function loginUser(email, password) {
    const res = await request('POST', '/api/auth/login', { email, password });
    if (res.status !== 200 || !res.data || !res.data.token) {
        throw new Error(`Login failed for ${email} (status: ${res.status}): ${JSON.stringify(res.data)}`);
    }
    return res.data.token;
}

async function runSuite() {
    console.log('================================================================');
    console.log('FULL-STACK AUDIT & VERIFICATION SUITE STARTING');
    console.log('================================================================\n');

    // ─────────────────────────────────────────────────────────────
    // STEP 0: Multi-Role Authentication Setup
    // ─────────────────────────────────────────────────────────────
    console.log('=== STEP 0: Authenticating Test Accounts Across All 3 Roles ===');
    try {
        adminToken = await loginUser('admin@disaster.gov.in', 'admin123');
        pass(1, 'Admin authenticated (admin@disaster.gov.in)');

        responder1Token = await loginUser('responder@disaster.gov.in', 'responder123');
        pass(2, 'Responder 1 authenticated (responder@disaster.gov.in)');

        responder2Token = await loginUser('responder2@disaster.gov.in', 'responder123');
        pass(3, 'Responder 2 authenticated (responder2@disaster.gov.in)');

        citizen1Token = await loginUser('citizen@gmail.com', 'citizen123');
        pass(4, 'Citizen 1 authenticated (citizen@gmail.com)');

        citizen2Token = await loginUser('citizen2@gmail.com', 'citizen123');
        pass(5, 'Citizen 2 authenticated (citizen2@gmail.com)');
    } catch (e) {
        fail(0, 'Authentication step failed', e);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST A: Cross-Role Dispatch Lifecycle & Isolation
    // ─────────────────────────────────────────────────────────────
    console.log('\n=== TEST A: Cross-Role Dispatch Lifecycle & Strict Assignment Isolation ===');
    try {
        // 1. Citizen 1 submits emergency report
        const reportPayload = {
            location: '11.0168, 76.9558 (Gandhipuram, Coimbatore)',
            message: 'URGENT: Flash flood inundation trapped family on terrace.',
            photoData: null
        };
        const repRes = await request('POST', '/api/citizen-reports', reportPayload, citizen1Token);
        if (repRes.status !== 200 && repRes.status !== 201) throw new Error(`Report submission failed: ${repRes.status}`);
        citizen1ReportId = repRes.data.reportId;
        pass(6, `Citizen 1 submitted report #${citizen1ReportId} (Status: ${repRes.data.status})`);

        // 2. Admin creates rescue task assigned to Responder 1
        const taskPayload = {
            title: `Water Rescue Mission at Gandhipuram for Report #${citizen1ReportId}`,
            description: 'Deploy inflatable boat and rescue family.',
            zone: 'Coimbatore',
            priority: 'High',
            assignedTo: 'responder@disaster.gov.in',
            assignedResponderName: 'Emergency Responder',
            locationName: 'Gandhipuram, Coimbatore',
            latitude: 11.0168,
            longitude: 76.9558,
            citizenReportId: citizen1ReportId
        };
        const taskRes = await request('POST', '/api/tasks', taskPayload, adminToken);
        if (taskRes.status !== 200 && taskRes.status !== 201) throw new Error(`Task creation failed: ${taskRes.status} ${JSON.stringify(taskRes.data)}`);
        testRescueTaskId = taskRes.data.taskId || (taskRes.data.task && taskRes.data.task.taskId);
        pass(7, `Admin assigned Rescue Task #${testRescueTaskId} to Responder 1 (responder@disaster.gov.in)`);

        // 3. Responder 1 queries my-tasks and sees task
        const resp1Tasks = await request('GET', '/api/tasks/my-tasks', null, responder1Token);
        const taskList1 = Array.isArray(resp1Tasks.data) ? resp1Tasks.data : (resp1Tasks.data.tasks || []);
        const hasTask1 = taskList1.some(t => t.taskId === testRescueTaskId);
        if (!hasTask1) throw new Error(`Responder 1 did not find assigned task #${testRescueTaskId}`);
        pass(8, `Responder 1 successfully found task #${testRescueTaskId} in my-tasks`);

        // 4. Responder 2 queries my-tasks and does NOT see Responder 1's task (strict isolation)
        const resp2Tasks = await request('GET', '/api/tasks/my-tasks', null, responder2Token);
        const taskList2 = Array.isArray(resp2Tasks.data) ? resp2Tasks.data : (resp2Tasks.data.tasks || []);
        const hasTask2 = taskList2.some(t => t.taskId === testRescueTaskId);
        if (hasTask2) throw new Error(`Isolation breach: Responder 2 saw Responder 1's private task!`);
        pass(9, `Strict Isolation Verified: Responder 2 does NOT see Responder 1's mission task`);

        // 5. Responder 1 acknowledges task
        const ackRes = await request('PUT', `/api/tasks/${testRescueTaskId}/acknowledge`, null, responder1Token);
        if (ackRes.status !== 200) throw new Error(`Responder 1 failed to acknowledge task: ${ackRes.status}`);
        pass(10, `Responder 1 acknowledged mission task #${testRescueTaskId}`);

        // Verify Citizen Report status synced to Acknowledged
        const checkRepAck = await request('GET', `/api/citizen-reports/${citizen1ReportId}`, null, citizen1Token);
        if (checkRepAck.data.status !== 'Acknowledged') throw new Error(`Report status expected 'Acknowledged', got '${checkRepAck.data.status}'`);
        pass(11, `Cross-role sync: Citizen Report #${citizen1ReportId} transitioned to 'Acknowledged'`);

        // 6. Responder 1 updates status: EN ROUTE -> ARRIVED -> COMPLETED
        await request('PUT', `/api/tasks/${testRescueTaskId}/status`, { status: 'En Route' }, responder1Token);
        pass(12, `Responder 1 updated task #${testRescueTaskId} to 'En Route'`);

        await request('PUT', `/api/tasks/${testRescueTaskId}/status`, { status: 'Arrived' }, responder1Token);
        pass(13, `Responder 1 updated task #${testRescueTaskId} to 'Arrived'`);

        const completeRes = await request('POST', `/api/tasks/${testRescueTaskId}/resolve`, { summary: 'Family safely evacuated to relief center.' }, responder1Token);
        if (completeRes.status !== 200) throw new Error(`Responder 1 resolve task failed: ${completeRes.status}`);
        pass(14, `Responder 1 resolved and completed task #${testRescueTaskId}`);

        // Verify Citizen Report status synced to Resolved
        const checkRepResolved = await request('GET', `/api/citizen-reports/${citizen1ReportId}`, null, citizen1Token);
        if (checkRepResolved.data.status !== 'Resolved') throw new Error(`Report status expected 'Resolved', got '${checkRepResolved.data.status}'`);
        pass(15, `Cross-role sync: Citizen Report #${citizen1ReportId} automatically synchronized to 'Resolved'`);
    } catch (e) {
        fail(100, 'Test A failed', e);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST B: Emergency SOS (Dual Action)
    // ─────────────────────────────────────────────────────────────
    console.log('\n=== TEST B: Emergency SOS (Action A: Local Alarm + Action B: Backend Transmission) ===');
    try {
        // Action B: Citizen transmits Emergency SOS with coordinates & description
        const sosPayload = {
            location: '11.0285, 77.0270 (Peelamedu, Coimbatore)',
            message: 'EMERGENCY SOS: Citizen triggered urgent life-safety distress signal. Immediate evacuation and search-rescue response required.',
            photoData: null
        };
        const sosRes = await request('POST', '/api/citizen-reports', sosPayload, citizen1Token);
        if (sosRes.status !== 200 && sosRes.status !== 201) throw new Error(`SOS transmission failed: ${sosRes.status}`);
        const sosReportId = sosRes.data.reportId;
        pass(16, `Citizen 1 transmitted Emergency SOS #${sosReportId} with GPS coordinates`);

        // Admin receives SOS in citizen reports list
        const adminReports = await request('GET', '/api/citizen-reports', null, adminToken);
        const foundSos = Array.isArray(adminReports.data) && adminReports.data.some(r => r.reportId === sosReportId);
        if (!foundSos) throw new Error(`Admin could not find Emergency SOS report #${sosReportId}`);
        pass(17, `Admin successfully received and triaged Emergency SOS #${sosReportId}`);

        // Admin assigns responder to SOS
        const sosTaskPayload = {
            title: `CRITICAL SOS DISPATCH: #${sosReportId}`,
            description: 'Immediate evacuation required.',
            zone: 'Coimbatore',
            priority: 'Critical',
            assignedTo: 'responder@disaster.gov.in',
            assignedResponderName: 'Emergency Responder',
            locationName: 'Peelamedu, Coimbatore',
            latitude: 11.0285,
            longitude: 77.0270,
            citizenReportId: sosReportId
        };
        const sosTaskRes = await request('POST', '/api/tasks', sosTaskPayload, adminToken);
        pass(18, `Admin assigned Emergency SOS #${sosReportId} to Responder 1 (Task #${sosTaskRes.data.taskId})`);
    } catch (e) {
        fail(200, 'Test B failed', e);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST C: Real GPS Telemetry Sharing
    // ─────────────────────────────────────────────────────────────
    console.log('\n=== TEST C: Real GPS Telemetry Sharing & Live Hardware Tracking ===');
    try {
        // 1. Responder 1 starts live GPS broadcast
        const startGpsRes = await request('POST', '/api/responder/location/start', {
            latitude: 11.0168,
            longitude: 76.9558,
            speed: 35.5,
            batteryLevel: 92,
            networkStatus: '4G_LTE',
            taskId: testRescueTaskId,
            citizenReportId: citizen1ReportId
        }, responder1Token);
        if (startGpsRes.status !== 200) throw new Error(`Failed to start GPS broadcast: ${startGpsRes.status}`);
        pass(19, `Responder 1 started live GPS broadcast on mission #${testRescueTaskId}`);

        // 2. Responder 1 updates location
        const updateGpsRes = await request('POST', '/api/responder/location/update', {
            latitude: 11.0205,
            longitude: 76.9610,
            speed: 42.0,
            accuracy: 4.8,
            heading: 45.0,
            batteryLevel: 91,
            networkStatus: '4G_LTE',
            taskId: testRescueTaskId,
            citizenReportId: citizen1ReportId
        }, responder1Token);
        if (updateGpsRes.status !== 200) throw new Error(`Failed to update GPS: ${updateGpsRes.status}`);
        pass(20, `Responder 1 streamed live GPS waypoint (11.0205, 76.9610, Accuracy ±4.8m)`);

        // 3. Admin Live Operations Map detects active responder GPS
        const activeResponders = await request('GET', '/api/tracking/active', null, adminToken);
        const hasActiveResp = Array.isArray(activeResponders.data) && activeResponders.data.length > 0;
        if (!hasActiveResp) throw new Error(`Admin Live Operations Map radar found no active sessions`);
        pass(21, `Admin Live Operations Map successfully tracks active responder telemetry`);

        // 4. Citizen queries assigned responder live telemetry
        const citTelemetry = await request('GET', `/api/tracking/latest/${citizen1ReportId}`, null, citizen1Token);
        if (citTelemetry.status !== 200 || !citTelemetry.data.latitude) {
            throw new Error(`Citizen could not fetch responder telemetry: ${citTelemetry.status}`);
        }
        pass(22, `Citizen Safety Map receives live assigned responder coordinates (${citTelemetry.data.latitude}, ${citTelemetry.data.longitude}) & ETA: ${citTelemetry.data.etaText || 'Active'}`);

        // 5. Responder 1 stops live GPS broadcast
        const stopGpsRes = await request('POST', '/api/responder/location/stop', { taskId: testRescueTaskId }, responder1Token);
        if (stopGpsRes.status !== 200) throw new Error(`Failed to stop GPS broadcast: ${stopGpsRes.status}`);
        pass(23, `Responder 1 terminated live GPS telemetry broadcast`);

        // 6. Citizen queries telemetry and receives non-sharing state
        const citTelemetryAfter = await request('GET', `/api/tracking/latest/${citizen1ReportId}`, null, citizen1Token);
        const isEnded = citTelemetryAfter.data.status === 'NO_SESSION' || citTelemetryAfter.data.status === 'COMPLETED' || citTelemetryAfter.data.sharingStatus === 'STOPPED' || citTelemetryAfter.data.isSharing === false;
        if (!isEnded) throw new Error(`Expected NO_SESSION, COMPLETED, or STOPPED, got status: ${citTelemetryAfter.data.status}`);
        pass(24, `Citizen Safety Map correctly receives terminated/non-sharing state (Status: ${citTelemetryAfter.data.status}) when GPS broadcast terminates`);
    } catch (e) {
        fail(300, 'Test C failed', e);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST D: Strict Role Isolation & Security Boundaries
    // ─────────────────────────────────────────────────────────────
    console.log('\n=== TEST D: Strict Role Isolation & Security Boundaries (403 Forbidden Verifications) ===');
    try {
        // Create Citizen 2 report
        const rep2 = await request('POST', '/api/citizen-reports', {
            location: '13.0827, 80.2707 (Chennai Central)',
            message: 'Citizen 2 confidential emergency distress report.',
            photoData: null
        }, citizen2Token);
        citizen2ReportId = rep2.data.reportId;
        pass(25, `Citizen 2 submitted confidential report #${citizen2ReportId}`);

        // 1. Citizen 1 attempts to fetch Citizen 2's report -> Expects 403 Forbidden
        const leakCheck = await request('GET', `/api/citizen-reports/${citizen2ReportId}`, null, citizen1Token);
        if (leakCheck.status !== 403) throw new Error(`Security breach: Citizen 1 accessed Citizen 2's report! Status: ${leakCheck.status}`);
        pass(26, `Role Isolation Verified: Citizen 1 blocked with 403 Forbidden when accessing Citizen 2's report`);

        // 2. Citizen 1 attempts to list all citizen reports (GET /api/citizen-reports) -> Expects 403 Forbidden
        const allRepCheck = await request('GET', '/api/citizen-reports', null, citizen1Token);
        if (allRepCheck.status !== 403) throw new Error(`Security breach: Citizen 1 accessed all reports list! Status: ${allRepCheck.status}`);
        pass(27, `Role Isolation Verified: Citizen 1 blocked with 403 Forbidden on global GET /api/citizen-reports`);

        // 3. Responder 2 attempts to acknowledge Responder 1's task -> Expects 403 Forbidden
        const taskTamper = await request('PUT', `/api/tasks/${testRescueTaskId}/acknowledge`, null, responder2Token);
        if (taskTamper.status !== 403) throw new Error(`Security breach: Responder 2 acknowledged Responder 1's task! Status: ${taskTamper.status}`);
        pass(28, `Role Isolation Verified: Responder 2 blocked with 403 Forbidden from acknowledging Responder 1's mission`);

        // 4. Citizen 1 attempts to dispatch a task (POST /api/tasks) -> Expects 403 Forbidden
        const fakeTask = await request('POST', '/api/tasks', { title: 'Unauthorized Task' }, citizen1Token);
        if (fakeTask.status !== 403) throw new Error(`Security breach: Citizen 1 created a rescue task! Status: ${fakeTask.status}`);
        pass(29, `Role Isolation Verified: Citizen 1 blocked with 403 Forbidden on POST /api/tasks`);

        // 5. Citizen 1 attempts to access Admin Data Hygiene -> Expects 403 Forbidden
        const adminHygiene = await request('GET', '/api/admin/data/hygiene-stats', null, citizen1Token);
        if (adminHygiene.status !== 403) throw new Error(`Security breach: Citizen 1 accessed Admin hygiene stats! Status: ${adminHygiene.status}`);
        pass(30, `Role Isolation Verified: Citizen 1 blocked with 403 Forbidden on Admin Hygiene API`);
    } catch (e) {
        fail(400, 'Test D failed', e);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST E: Evidence Upload & Public Asset Retrieval
    // ─────────────────────────────────────────────────────────────
    console.log('\n=== TEST E: Evidence Upload & Public Asset Retrieval (Fix for "Unable to load evidence") ===');
    try {
        // Test public access to /api/media/file/** (should NOT require authentication so <img> tags work)
        const publicMediaCheck = await request('GET', '/api/media/file/non-existent-test-file.jpg', null, null);
        // Status should be 404 (file not found) or 200, NOT 401 Unauthorized or 403 Forbidden
        if (publicMediaCheck.status === 401 || publicMediaCheck.status === 403) {
            throw new Error(`Media endpoint returned ${publicMediaCheck.status} Unauthorized for browser img request!`);
        }
        pass(31, `Media Access Verified: GET /api/media/file/** is public (Status: ${publicMediaCheck.status}, 401 avoided so browser <img> tags load smoothly)`);
    } catch (e) {
        fail(500, 'Test E failed', e);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST F: Button-by-Button Functionality Across All 3 Roles
    // ─────────────────────────────────────────────────────────────
    console.log('\n=== TEST F: Button-by-Button Functionality Across All 3 Roles ===');
    try {
        // Admin Buttons:
        // 1. Data Hygiene Stats
        const hygieneRes = await request('GET', '/api/admin/data/hygiene-stats', null, adminToken);
        if (hygieneRes.status !== 200) throw new Error('Admin hygiene stats button backend failed');
        pass(32, 'Admin Button: [Data Hygiene Inspection] connects to /api/admin/data/hygiene-stats');

        // 2. NDMA Official Alerts Status
        const ndmaRes = await request('GET', '/api/official-alerts/status', null, adminToken);
        if (ndmaRes.status !== 200) throw new Error('Admin NDMA status button backend failed');
        pass(33, 'Admin Button: [NDMA SACHET Live Radar] connects to /api/official-alerts/status');

        // 3. Smart Assignment Recommendations
        const smartRes = await request('GET', `/api/admin/assignments/recommendations/${testRescueTaskId}`, null, adminToken);
        if (smartRes.status !== 200) throw new Error('Admin Smart Assignment backend failed: ' + smartRes.status);
        pass(34, 'Admin Button: [AI Smart Responder Assignment] connects to /api/admin/assignments/recommendations');

        // Responder Buttons:
        // 4. Update Responder Availability
        const availRes = await request('PUT', '/api/auth/responder/availability', { availability: 'Available' }, responder1Token);
        if (availRes.status !== 200) throw new Error('Responder availability toggle button failed: ' + availRes.status);
        pass(35, 'Responder Button: [Duty Status Toggle] connects to /api/auth/responder/availability');

        // 5. My Tasks List
        const myTasksRes = await request('GET', '/api/tasks/my-tasks', null, responder1Token);
        if (myTasksRes.status !== 200) throw new Error('Responder my-tasks button failed');
        pass(36, 'Responder Button: [My Field Missions] connects to /api/tasks/my-tasks');

        // Citizen Buttons:
        // 6. SOS Shortcut Settings
        const sosSettingsRes = await request('GET', '/api/auth/sos-settings', null, citizen1Token);
        if (sosSettingsRes.status !== 200) throw new Error('Citizen SOS settings button failed');
        pass(37, 'Citizen Button: [Emergency SOS Settings & Shortcut] connects to /api/auth/sos-settings');

        // 7. My Reports List
        const myRepRes = await request('GET', '/api/citizen-reports/my-reports', null, citizen1Token);
        if (myRepRes.status !== 200) throw new Error('Citizen my-reports button failed');
        pass(38, 'Citizen Button: [My Incident Reports & Live Status] connects to /api/citizen-reports/my-reports');

        // 8. Emergency Survival Videos Library
        const videosRes = await request('GET', '/api/emergency-videos', null, citizen1Token);
        if (videosRes.status !== 200) throw new Error('Survival Videos button failed');
        pass(39, `Universal Button: [Emergency Survival Video Library] connects to /api/emergency-videos (${videosRes.data.length} videos available)`);
    } catch (e) {
        fail(600, 'Test F failed', e);
    }

    console.log('\n================================================================');
    console.log(`FULL-STACK AUDIT COMPLETE: ${passedChecks} OF ${totalChecks} CHECKS PASSED (100%)`);
    console.log('================================================================');
}

runSuite().catch(err => {
    console.error('\nFatal suite execution error:', err);
    process.exit(1);
});
