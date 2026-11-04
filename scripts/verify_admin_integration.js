const http = require('http');

const BASE_URL = 'http://localhost:8080';

function request(method, path, body = null, headers = {}) {
    return new Promise((resolve, reject) => {
        const url = new URL(path, BASE_URL);
        const reqHeaders = { ...headers };
        let reqBody = null;
        if (body) {
            reqBody = typeof body === 'string' ? body : JSON.stringify(body);
            reqHeaders['Content-Type'] = 'application/json';
            reqHeaders['Content-Length'] = Buffer.byteLength(reqBody);
        }

        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: method,
            headers: reqHeaders
        };

        const req = http.request(options, (res) => {
            let resData = '';
            res.on('data', (chunk) => { resData += chunk; });
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(resData);
                } catch (e) {
                    parsed = resData;
                }
                resolve({
                    status: res.statusCode,
                    headers: res.headers,
                    data: parsed
                });
            });
        });

        req.on('error', (err) => reject(err));
        if (reqBody) req.write(reqBody);
        req.end();
    });
}

async function runVerification() {
    console.log('===============================================================');
    console.log('MASTER END-TO-END INTEGRATION TEST SUITE');
    console.log('Disaster Management & Emergency Response Coordination System');
    console.log('===============================================================\n');

    let allPassed = true;
    function assert(condition, message) {
        if (condition) {
            console.log(`  [PASS] ${message}`);
        } else {
            console.error(`  [FAIL] ${message}`);
            allPassed = false;
        }
    }

    // -------------------------------------------------------------------------
    // TEST 1: THREE-ROLE AUTHENTICATION
    // -------------------------------------------------------------------------
    console.log('--- 1. Testing Three-Role Authentication ---');
    const adminLogin = await request('POST', '/api/auth/login', { email: 'admin@disaster.gov.in', password: 'admin123' });
    assert(adminLogin.status === 200 && adminLogin.data.token, 'Admin login succeeded (200 OK)');
    const adminToken = adminLogin.data.token;

    const respLogin = await request('POST', '/api/auth/login', { email: 'responder@disaster.gov.in', password: 'responder123' });
    assert(respLogin.status === 200 && respLogin.data.token, 'Responder login succeeded (200 OK)');
    const respToken = respLogin.data.token;

    const citLogin = await request('POST', '/api/auth/login', { email: 'citizen@gmail.com', password: 'citizen123' });
    assert(citLogin.status === 200 && citLogin.data.token, 'Citizen login succeeded (200 OK)');
    const citToken = citLogin.data.token;

    // -------------------------------------------------------------------------
    // TEST 2: ISSUE 1 & 2 - ALERTS & ACTIONS
    // -------------------------------------------------------------------------
    console.log('\n--- 2. Testing Broadcast Alerts Management & Card Actions ---');
    const createAlert = await request('POST', '/api/alerts', {
        title: 'Integration Test Warning Coastal Surge',
        message: 'Severe storm surge advisory for test sector. Evacuate low terrain.',
        disasterType: 'Flood',
        severity: 'Medium',
        region: 'Chennai',
        broadcasted: true
    }, { 'Authorization': `Bearer ${adminToken}` });
    const alertId = (createAlert.data && createAlert.data.alert) ? createAlert.data.alert.alertId : createAlert.data.alertId;
    assert((createAlert.status === 200 || createAlert.status === 201) && alertId, `Alert created successfully (ID: ${alertId})`);

    // Acknowledge Alert
    const ackAlert = await request('PUT', `/api/alerts/${alertId}/acknowledge`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(ackAlert.status === 200, `Alert #${alertId} acknowledged (200 OK)`);

    // Incident Room for Alert
    const alertRoom = await request('GET', `/api/incidents/${alertId}/room?type=ALERT_BROADCAST`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(alertRoom.status === 200 && alertRoom.data.alert, `Incident Room loaded real alert data (200 OK, Disaster: ${alertRoom.data.alert.disasterType})`);

    // Resolve Alert
    const resAlert = await request('PUT', `/api/alerts/${alertId}/resolve`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(resAlert.status === 200, `Alert #${alertId} resolved (200 OK)`);

    // Delete Alert
    const delAlert = await request('DELETE', `/api/alerts/${alertId}`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(delAlert.status === 200, `Alert #${alertId} deleted (200 OK)`);

    // -------------------------------------------------------------------------
    // TEST 3: RESCUE TASK LIFECYCLE & CROSS-ROLE SYNCHRONIZATION
    // -------------------------------------------------------------------------
    console.log('\n--- 3. Testing Task Lifecycle & Cross-Role Synchronizations ---');
    const createTask = await request('POST', '/api/tasks', {
        title: 'Emergency Medical Evacuation - Sector 4',
        description: 'Citizen evacuation team required immediately.',
        zone: 'Chennai',
        priority: 'High',
        assignedTo: 'responder@disaster.gov.in',
        locationName: 'Marina Beach Road'
    }, { 'Authorization': `Bearer ${adminToken}` });
    assert(createTask.status === 200 && createTask.data.task, `Task created (ID: ${createTask.data.task.taskId})`);
    const taskId = createTask.data.task.taskId;

    // Responder Acknowledges Task
    const ackTask = await request('PUT', `/api/tasks/${taskId}/acknowledge`, null, { 'Authorization': `Bearer ${respToken}` });
    assert(ackTask.status === 200, `Responder acknowledged Task #${taskId} (200 OK)`);

    // Incident Room for Rescue Task
    const taskRoom = await request('GET', `/api/incidents/${taskId}/room?type=RESCUE_TASK`, null, { 'Authorization': `Bearer ${respToken}` });
    assert(taskRoom.status === 200 && taskRoom.data.task, `Incident Room loaded real rescue task data (200 OK)`);

    // Responder Resolves Task with Summary
    const resTask = await request('PUT', `/api/tasks/${taskId}/resolve?summary=Evacuation+completed+safely`, null, { 'Authorization': `Bearer ${respToken}` });
    assert(resTask.status === 200 && resTask.data.task.status === 'Completed', `Responder resolved Task #${taskId} with operational summary (200 OK)`);

    // Admin Deletes Completed Task
    const delTask = await request('DELETE', `/api/tasks/${taskId}`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(delTask.status === 200, `Task #${taskId} deleted by Admin (200 OK)`);

    // -------------------------------------------------------------------------
    // TEST 4: ADMIN SECURITY PIN RESET & EMERGENCY RECOVERY WORKFLOW
    // -------------------------------------------------------------------------
    console.log('\n--- 4. Testing Admin Security PIN Reset & Recovery Workflow ---');
    // Enroll PIN
    const enrollRes = await request('POST', '/api/auth/admin/pin/enroll', {
        currentPassword: 'admin123',
        pin: '654321'
    }, { 'Authorization': `Bearer ${adminToken}` });
    assert(enrollRes.status === 200 && enrollRes.data.success && enrollRes.data.recoveryCode,
        `PIN enrolled successfully, single-use recovery code generated: ${enrollRes.data.recoveryCode}`);
    const recoveryCode = enrollRes.data.recoveryCode;

    // Verify PIN
    const verifyPin = await request('POST', '/api/auth/admin/verify-pin', { pin: '654321' }, { 'Authorization': `Bearer ${adminToken}` });
    assert(verifyPin.status === 200 && verifyPin.data.stepUpToken, 'PIN verified successfully and step-up token issued (200 OK)');
    const stepUpToken = verifyPin.data.stepUpToken;

    // Recover PIN using Emergency Recovery Code
    const recoverWithCode = await request('POST', '/api/auth/admin/pin/recover', {
        recoveryType: 'RECOVERY_CODE',
        recoverySecret: recoveryCode,
        newPin: '987654'
    }, { 'Authorization': `Bearer ${adminToken}` });
    assert(recoverWithCode.status === 200 && recoverWithCode.data.success,
        `PIN recovered using Emergency Recovery Code. Fresh code issued: ${recoverWithCode.data.recoveryCode}`);
    const freshCode = recoverWithCode.data.recoveryCode;

    // Recover PIN using Account Password
    const recoverWithPwd = await request('POST', '/api/auth/admin/pin/recover', {
        recoveryType: 'ACCOUNT_PASSWORD',
        recoverySecret: 'admin123',
        newPin: '123456'
    }, { 'Authorization': `Bearer ${adminToken}` });
    assert(recoverWithPwd.status === 200 && recoverWithPwd.data.success,
        `PIN recovered using Account Password. Fresh code issued: ${recoverWithPwd.data.recoveryCode}`);

    // Audit Trail access with Step-Up Token
    const verifyNewPin = await request('POST', '/api/auth/admin/verify-pin', { pin: '123456' }, { 'Authorization': `Bearer ${adminToken}` });
    const newStepUpToken = verifyNewPin.data.stepUpToken;
    const auditLogs = await request('GET', '/api/auth/admin/audit-logs', null, {
        'Authorization': `Bearer ${adminToken}`,
        'X-Admin-Step-Up-Token': newStepUpToken
    });
    assert(auditLogs.status === 200 && Array.isArray(auditLogs.data), `Audit logs retrieved successfully (${auditLogs.data.length} audit events logged)`);

    // -------------------------------------------------------------------------
    // TEST 5: ISSUE 5 - CITIZEN REPORTS PAGINATION & DELETION INTEGRITY
    // -------------------------------------------------------------------------
    console.log('\n--- 5. Testing Citizen Reports Pagination & Deletion Integrity ---');
    // Citizen Submits Report
    const subReport = await request('POST', '/api/citizen-reports', {
        location: 'Coimbatore Civil Station East',
        message: 'Severe tree fall obstructing emergency hospital entrance.',
        incidentType: 'Fallen Tree'
    }, { 'Authorization': `Bearer ${citToken}` });
    assert(subReport.status === 200 && subReport.data.reportId, `Citizen report submitted (ID: ${subReport.data.reportId})`);
    const reportId = subReport.data.reportId;

    // Test Pagination endpoint
    const pagedReports = await request('GET', '/api/citizen-reports/paged?page=0&size=10&search=&status=', null, { 'Authorization': `Bearer ${adminToken}` });
    assert(pagedReports.status === 200 && (pagedReports.data.reports || pagedReports.data.content) && pagedReports.data.totalElements > 0,
        `Paged citizen reports query succeeded (Total elements: ${pagedReports.data.totalElements}, Total pages: ${pagedReports.data.totalPages})`);

    // Convert Report to Task (Linking)
    const linkedTask = await request('POST', '/api/tasks', {
        title: `Clear Obstruction - Report #${reportId}`,
        description: 'Remove fallen tree from hospital approach.',
        zone: 'Coimbatore',
        priority: 'High',
        citizenReportId: reportId
    }, { 'Authorization': `Bearer ${adminToken}` });
    assert(linkedTask.status === 200 && linkedTask.data.task, `Linked task created for Report #${reportId} (Task ID: ${linkedTask.data.task.taskId})`);
    const linkedTaskId = linkedTask.data.task.taskId;

    // Integrity Check: Deletion of Citizen Report MUST fail while task is active (HTTP 409 Conflict)
    const deleteAttemptBlocked = await request('DELETE', `/api/citizen-reports/${reportId}?action=delete`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(deleteAttemptBlocked.status === 409, `Deletion constraint enforced: HTTP 409 Conflict returned (${deleteAttemptBlocked.data.message})`);

    // Now resolve the task
    const resolveLinkedTask = await request('PUT', `/api/tasks/${linkedTaskId}/resolve?summary=Tree+cleared+completely`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(resolveLinkedTask.status === 200, `Linked task #${linkedTaskId} resolved (200 OK)`);

    // Now delete citizen report (integrity constraint satisfied)
    const deleteReportSuccess = await request('DELETE', `/api/citizen-reports/${reportId}?action=delete`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(deleteReportSuccess.status === 200 && deleteReportSuccess.data.status === 'success',
        `Citizen report #${reportId} deleted successfully after task resolution (200 OK)`);

    // Archive Report Test (submit and archive)
    const reportForArchive = await request('POST', '/api/citizen-reports', {
        location: 'Salem Test Depot',
        message: 'Water logging near underpass.',
        incidentType: 'Water Logging'
    }, { 'Authorization': `Bearer ${citToken}` });
    const archiveReportRes = await request('PUT', `/api/citizen-reports/${reportForArchive.data.reportId}/archive`, null, { 'Authorization': `Bearer ${adminToken}` });
    assert(archiveReportRes.status === 200 && archiveReportRes.data.status === 'success', `Citizen report archived successfully (200 OK)`);

    // Cleanup archived report
    await request('DELETE', `/api/citizen-reports/${reportForArchive.data.reportId}?action=delete&permanent=true`, null, { 'Authorization': `Bearer ${adminToken}` });

    // Cleanup task
    await request('DELETE', `/api/tasks/${linkedTaskId}`, null, { 'Authorization': `Bearer ${adminToken}` });

    console.log('\n===============================================================');
    if (allPassed) {
        console.log('ALL VERIFICATION SUITES PASSED PERFECTLY (100% SUCCESS)');
    } else {
        console.error('SOME VERIFICATION CHECKS FAILED');
    }
    console.log('===============================================================');
}

runVerification().catch(err => {
    console.error('Fatal test runner error:', err);
    process.exit(1);
});
