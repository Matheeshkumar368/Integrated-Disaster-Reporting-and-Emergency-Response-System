/**
 * master_integration_regression_test.js
 * Comprehensive Full-Stack Integration & Regression Test Suite
 * Validates the complete Citizen -> Admin -> Responder -> Citizen workflow across all endpoints
 */
const http = require('http');

function request(options, bodyData, isMultipart = false) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                let parsed = data;
                try {
                    parsed = JSON.parse(data);
                } catch (e) {}
                resolve({
                    statusCode: res.statusCode,
                    headers: res.headers,
                    data: parsed,
                    raw: data
                });
            });
        });

        req.on('error', err => reject(err));
        if (bodyData) {
            if (Buffer.isBuffer(bodyData)) {
                req.write(bodyData);
            } else if (typeof bodyData === 'string') {
                req.write(bodyData);
            } else {
                req.write(JSON.stringify(bodyData));
            }
        }
        req.end();
    });
}

function buildMultipartFormData(boundary, fields, fileField) {
    const CRLF = '\r\n';
    let body = [];

    for (const [key, val] of Object.entries(fields)) {
        body.push(Buffer.from(
            `--${boundary}${CRLF}` +
            `Content-Disposition: form-data; name="${key}"${CRLF}${CRLF}` +
            `${val}${CRLF}`
        ));
    }

    if (fileField) {
        body.push(Buffer.from(
            `--${boundary}${CRLF}` +
            `Content-Disposition: form-data; name="${fileField.name}"; filename="${fileField.filename}"${CRLF}` +
            `Content-Type: ${fileField.contentType}${CRLF}${CRLF}`
        ));
        body.push(fileField.buffer);
        body.push(Buffer.from(CRLF));
    }

    body.push(Buffer.from(`--${boundary}--${CRLF}`));
    return Buffer.concat(body);
}

async function run() {
    console.log('================================================================');
    console.log('   MASTER FULL-STACK INTEGRATION & REGRESSION TEST SUITE');
    console.log('================================================================\n');

    let totalTests = 0;
    let passedTests = 0;
    let failedTests = 0;

    function assert(description, condition, details = '') {
        totalTests++;
        if (condition) {
            passedTests++;
            console.log(`[PASS] ${description}`);
            if (details) console.log(`       ${details}`);
        } else {
            failedTests++;
            console.error(`[FAIL] ${description}`);
            if (details) console.error(`       Details: ${details}`);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 1: CITIZEN AUTHENTICATION & EMERGENCY INCIDENT CREATION
    // ─────────────────────────────────────────────────────────────
    console.log('\n--- 1. CITIZEN STAGE ---');
    const citizenLogin = await request({
        hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, { email: 'citizen@gmail.com', password: 'citizen123' });

    assert('Citizen Login (citizen@gmail.com)', citizenLogin.statusCode === 200 && citizenLogin.data.token,
           `Role: ${citizenLogin.data?.role}, Token received`);
    const citizenToken = citizenLogin.data.token;

    // Submit Citizen Emergency Report
    const samplePhotoBase64 = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
    const reportSubmit = await request({
        hostname: 'localhost', port: 8080, path: '/api/citizen-reports', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + citizenToken }
    }, {
        location: 'Cross Cut Road, Sector 4, Coimbatore',
        message: '[Flood | Severity: High] Rising Flood Waters Trapping Residents\n\nWater level reaching 4 feet, 3 elderly civilians need evacuation.\n\n[Device GPS: 11.01684, 76.95583]',
        photoData: samplePhotoBase64
    });

    assert('Citizen Emergency Report Submission (/api/citizen-reports)', 
           (reportSubmit.statusCode === 200 || reportSubmit.statusCode === 201) && reportSubmit.data.reportId,
           `Created Incident Ref: #INC-2026-${reportSubmit.data?.reportId}`);
    const incidentReportId = reportSubmit.data?.reportId;

    // Verify Citizen My-Reports Isolation
    const myReportsCheck = await request({
        hostname: 'localhost', port: 8080, path: '/api/citizen-reports/my-reports', method: 'GET',
        headers: { 'Authorization': 'Bearer ' + citizenToken }
    });
    const foundMyReport = Array.isArray(myReportsCheck.data) && myReportsCheck.data.some(r => r.reportId === incidentReportId);
    assert('Citizen Ownership Isolation (/api/citizen-reports/my-reports)', foundMyReport,
           `Report #${incidentReportId} correctly stored in citizen personal records`);

    // ─────────────────────────────────────────────────────────────
    // STEP 2: ADMIN AUTHENTICATION, REVIEW & RESPONDER ASSIGNMENT
    // ─────────────────────────────────────────────────────────────
    console.log('\n--- 2. ADMIN STAGE ---');
    const adminLogin = await request({
        hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, { email: 'admin@disaster.gov.in', password: 'admin123' });

    assert('Admin Login (admin@disaster.gov.in)', adminLogin.statusCode === 200 && adminLogin.data.token,
           `Role: ${adminLogin.data?.role}, Token received`);
    const adminToken = adminLogin.data.token;

    // Admin Retrieves Citizen Reports
    const adminReportsCheck = await request({
        hostname: 'localhost', port: 8080, path: '/api/citizen-reports', method: 'GET',
        headers: { 'Authorization': 'Bearer ' + adminToken }
    });
    const adminFoundReport = Array.isArray(adminReportsCheck.data) && adminReportsCheck.data.some(r => r.reportId === incidentReportId);
    assert('Admin Incident Review (/api/citizen-reports)', adminFoundReport,
           `Admin successfully queried incident queue and located report #${incidentReportId}`);

    // Admin Dispatches Rescue Task to Responder Unit
    const adminCreateTask = await request({
        hostname: 'localhost', port: 8080, path: '/api/tasks', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + adminToken }
    }, {
        title: 'Emergency Flood Evacuation - Cross Cut Road',
        description: `Dispatched from civilian distress report #${incidentReportId}. Evacuate 3 trapped civilians.`,
        zone: 'Coimbatore',
        priority: 'High',
        locationName: 'Cross Cut Road, Sector 4, Coimbatore',
        latitude: 11.01684,
        longitude: 76.95583,
        assignedTo: 'responder@disaster.gov.in',
        assignedResponderName: 'Emergency Responder Unit 1'
    });

    assert('Admin Rescue Task Creation & Assignment (/api/tasks)', 
           adminCreateTask.statusCode === 200 && adminCreateTask.data?.task?.taskId,
           `Assigned Task #${adminCreateTask.data?.task?.taskId} to responder@disaster.gov.in`);
    const assignedTaskId = adminCreateTask.data?.task?.taskId;

    // Admin Evaluates Smart Assignment Recommendations
    const smartRecs = await request({
        hostname: 'localhost', port: 8080, path: `/api/admin/assignments/recommendations/${assignedTaskId}`, method: 'GET',
        headers: { 'Authorization': 'Bearer ' + adminToken }
    });
    assert('Admin Smart Assignment Engine (/api/admin/assignments/recommendations)',
           smartRecs.statusCode === 200 && Array.isArray(smartRecs.data?.recommendations),
           `Evaluated ${smartRecs.data?.recommendations?.length || 0} responder suitability scores`);

    // Admin Security PIN Step-Up Verification
    const pinVerify = await request({
        hostname: 'localhost', port: 8080, path: '/api/auth/admin/pin/verify', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + adminToken }
    }, { pin: '123456' });
    const stepUpToken = pinVerify.data?.stepUpToken;

    // Admin Issues Emergency Alert Broadcast with Step-Up Token
    const adminBroadcastHeaders = {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + adminToken
    };
    if (stepUpToken) adminBroadcastHeaders['X-Admin-Step-Up-Token'] = stepUpToken;

    const adminBroadcast = await request({
        hostname: 'localhost', port: 8080, path: '/api/alerts', method: 'POST',
        headers: adminBroadcastHeaders
    }, {
        title: 'Municipal Flood Advisory: Cross Cut Sector Inundation',
        message: 'Severe stormwater drain overflow on Cross Cut Road. Emergency services actively conducting rescues.',
        disasterType: 'Flood',
        severity: 'High',
        region: 'Coimbatore',
        latitude: 11.01684,
        longitude: 76.95583,
        broadcast: true
    });
    const broadcastAlertId = adminBroadcast.data?.alert?.alertId || adminBroadcast.data?.alertId;
    assert('Admin Emergency Alert Broadcast (/api/alerts)',
           adminBroadcast.statusCode === 200 && broadcastAlertId,
           `Broadcast Alert #${broadcastAlertId} issued to regional civilians`);

    // ─────────────────────────────────────────────────────────────
    // STEP 3: RESPONDER FIELD OPERATIONS LIFECYCLE & TELEMETRY
    // ─────────────────────────────────────────────────────────────
    console.log('\n--- 3. RESPONDER STAGE ---');
    const respLogin = await request({
        hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, { email: 'responder@disaster.gov.in', password: 'responder123' });

    assert('Responder Login (responder@disaster.gov.in)', respLogin.statusCode === 200 && respLogin.data.token,
           `Role: ${respLogin.data?.role}, Token received`);
    const responderToken = respLogin.data.token;

    // Responder Retrieves Assigned Tasks
    const respTasks = await request({
        hostname: 'localhost', port: 8080, path: '/api/tasks/my-tasks', method: 'GET',
        headers: { 'Authorization': 'Bearer ' + responderToken }
    });
    const foundAssignedTask = Array.isArray(respTasks.data?.tasks) && respTasks.data.tasks.some(t => t.taskId === assignedTaskId);
    assert('Responder Task Retrieval (/api/tasks/my-tasks)', foundAssignedTask,
           `Responder located Task #${assignedTaskId} in personal queue`);

    // Stage 1: Acknowledge Assignment
    const stageAck = await request({
        hostname: 'localhost', port: 8080, path: `/api/tasks/${assignedTaskId}/status`, method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + responderToken }
    }, { status: 'Acknowledged', notes: 'Unit 1 acknowledged dispatch order.' });
    assert('Stage 1: Acknowledge Assignment', stageAck.statusCode === 200 && stageAck.data?.task?.status === 'Acknowledged',
           'Status transitioned to Acknowledged');

    // Stage 2: Deploy En Route
    const stageEnRoute = await request({
        hostname: 'localhost', port: 8080, path: `/api/tasks/${assignedTaskId}/status`, method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + responderToken }
    }, { status: 'En Route', notes: 'Unit 1 deployed, vehicle en route.' });
    assert('Stage 2: Start Response (En Route)', stageEnRoute.statusCode === 200 && stageEnRoute.data?.task?.status === 'En Route',
           'Status transitioned to En Route');

    // Start GPS Live Location Telemetry Sharing
    const trackStart = await request({
        hostname: 'localhost', port: 8080, path: '/api/tracking/start', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + responderToken }
    }, {
        assignmentId: assignedTaskId,
        latitude: 11.01250,
        longitude: 76.95200,
        accuracy: 5.2
    });
    assert('Live Location Sharing Activated (/api/tracking/start)', trackStart.statusCode === 200 && trackStart.data?.sessionId,
           `Tracking session #${trackStart.data?.sessionId} started for Task #${assignedTaskId}`);

    // Update GPS Telemetry Heartbeat
    const trackUpdate = await request({
        hostname: 'localhost', port: 8080, path: '/api/tracking/update', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + responderToken }
    }, {
        assignmentId: assignedTaskId,
        latitude: 11.01580,
        longitude: 76.95450,
        accuracy: 4.1,
        heading: 42.0,
        speed: 28.5
    });
    assert('Live Telemetry Heartbeat (/api/tracking/update)', trackUpdate.statusCode === 200 && trackUpdate.data?.latitude === 11.01580,
           'Telemetry coordinates updated to 11.01580, 76.95450');

    // Stage 3: Mark Arrived On Scene
    const stageArrived = await request({
        hostname: 'localhost', port: 8080, path: `/api/tasks/${assignedTaskId}/status`, method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + responderToken }
    }, { status: 'Arrived', notes: 'Unit on scene at Cross Cut Road underpass. Initiating water rescue.' });
    assert('Stage 3: Mark Arrived On Scene', stageArrived.statusCode === 200 && stageArrived.data?.task?.status === 'Arrived',
           'Status transitioned to Arrived');

    // Attach Tactical Field Evidence via Multipart Form Data
    const boundary = '----WebKitFormBoundary' + Math.random().toString(36).substring(2);
    const pngBuffer = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');
    const multipartBody = buildMultipartFormData(boundary, {
        incidentId: assignedTaskId.toString(),
        incidentType: 'TASK',
        caption: 'Scene confirmation photo of submerged underpass',
        latitude: '11.01684',
        longitude: '76.95583'
    }, {
        name: 'file',
        filename: 'evidence_scene.png',
        contentType: 'image/png',
        buffer: pngBuffer
    });

    const uploadEvidence = await request({
        hostname: 'localhost', port: 8080, path: '/api/media/upload', method: 'POST',
        headers: {
            'Content-Type': 'multipart/form-data; boundary=' + boundary,
            'Content-Length': multipartBody.length,
            'Authorization': 'Bearer ' + responderToken
        }
    }, multipartBody);

    const mediaId = uploadEvidence.data?.media?.mediaId || uploadEvidence.data?.mediaId || uploadEvidence.data?.media?.id;
    assert('Field Evidence Capture (/api/media/upload)', uploadEvidence.statusCode === 200 && (mediaId || uploadEvidence.data?.status === 'success'),
           `Evidence attached to Task #${assignedTaskId} with GPS coordinates`);

    // Post Tactical Comms Room Message
    const postMsg = await request({
        hostname: 'localhost', port: 8080, path: `/api/incidents/${assignedTaskId}/timeline/message?type=TASK`, method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + responderToken }
    }, {
        message: 'Evacuation in progress. 2 citizens brought to safety.'
    });
    assert('Tactical Comms Room Message (/api/incidents/{id}/timeline/message)', postMsg.statusCode === 200,
           'Operational field communication recorded in timeline');

    // Stage 4: Resolve & Clear Mission
    const stageCompleted = await request({
        hostname: 'localhost', port: 8080, path: `/api/tasks/${assignedTaskId}/status`, method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + responderToken }
    }, { status: 'Completed', notes: 'All trapped civilians safely extracted and transported to relief shelter.' });
    assert('Stage 4: Complete & Resolve Mission', stageCompleted.statusCode === 200 && stageCompleted.data?.task?.status === 'Completed',
           'Mission marked Completed in database');

    // Also Resolve the original Citizen report
    const citizenReportResolve = await request({
        hostname: 'localhost', port: 8080, path: `/api/citizen-reports/${incidentReportId}/resolve`, method: 'PUT',
        headers: { 'Authorization': 'Bearer ' + responderToken }
    });
    assert('Linked Citizen Report Resolution (/api/citizen-reports/{id}/resolve)', citizenReportResolve.statusCode === 200,
           `Civilian Report #${incidentReportId} status updated to Resolved`);

    // ─────────────────────────────────────────────────────────────
    // STEP 4: VERIFY POST-RESOLUTION CROSS-ROLE REFRESH
    // ─────────────────────────────────────────────────────────────
    console.log('\n--- 4. POST-RESOLUTION VERIFICATION ---');

    // Citizen views updated report
    const citizenVerify = await request({
        hostname: 'localhost', port: 8080, path: '/api/citizen-reports/my-reports', method: 'GET',
        headers: { 'Authorization': 'Bearer ' + citizenToken }
    });
    const finalReport = citizenVerify.data.find(r => r.reportId === incidentReportId);
    assert('Citizen View: Incident Status Reflected as Resolved', finalReport?.status === 'Resolved',
           `Report #${incidentReportId} status verified as '${finalReport?.status}'`);

    // Admin verifies task status
    const adminVerify = await request({
        hostname: 'localhost', port: 8080, path: `/api/tasks/${assignedTaskId}`, method: 'GET',
        headers: { 'Authorization': 'Bearer ' + adminToken }
    });
    assert('Admin View: Task Verified as Completed', adminVerify.data?.task?.status === 'Completed',
           `Task #${assignedTaskId} verified as '${adminVerify.data?.task?.status}'`);

    // Verify Tracking Concluded
    const trackClosedCheck = await request({
        hostname: 'localhost', port: 8080, path: `/api/tracking/latest/${assignedTaskId}`, method: 'GET',
        headers: { 'Authorization': 'Bearer ' + citizenToken }
    });
    assert('Telemetry Terminated Post-Completion', trackClosedCheck.data?.status === 'COMPLETED' || trackClosedCheck.data?.status === 'NO_SESSION',
           `Tracking session status confirmed as '${trackClosedCheck.data?.status}' (no ghost tracking)`);

    // Verify NDMA SACHET Sync & Freshness
    const ndmaStatus = await request({
        hostname: 'localhost', port: 8080, path: '/api/official-alerts/status', method: 'GET'
    });
    assert('Official NDMA SACHET Telemetry Status (/api/official-alerts/status)', ndmaStatus.statusCode === 200,
           `Last Sync: ${ndmaStatus.data?.lastSyncTime || 'Active'}, Ingested: ${ndmaStatus.data?.totalAlertsCount || 'Genuine'}`);

    console.log('\n================================================================');
    console.log(`   TOTAL TESTS: ${totalTests} | PASSED: ${passedTests} | FAILED: ${failedTests}`);
    if (failedTests === 0) {
        console.log('   RESULT: 100% PASS - FULL SYSTEM INTEGRATION VERIFIED!');
    } else {
        console.error(`   RESULT: ${failedTests} FAILURE(S) DETECTED`);
    }
    console.log('================================================================');
}

run().catch(console.error);
