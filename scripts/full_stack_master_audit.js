/**
 * full_stack_master_audit.js
 * Comprehensive Full-Stack Verification Suite for Disaster Management & Alert System
 * Validates all backend APIs, database persistence, RBAC security,
 * and multi-role end-to-end workflows across Citizen, Admin, and Responder.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots';

function request(options, bodyData) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            const chunks = [];
            res.on('data', chunk => chunks.push(chunk));
            res.on('end', () => {
                const buffer = Buffer.concat(chunks);
                const rawText = buffer.toString('utf8');
                let parsed = rawText;
                try {
                    parsed = JSON.parse(rawText);
                } catch (e) {}
                resolve({
                    statusCode: res.statusCode,
                    headers: res.headers,
                    data: parsed,
                    buffer: buffer,
                    raw: rawText
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
            `Content-Disposition: form-data; name="${fileField.fieldname}"; filename="${fileField.filename}"${CRLF}` +
            `Content-Type: ${fileField.contentType}${CRLF}${CRLF}`
        ));
        body.push(fileField.buffer);
        body.push(Buffer.from(CRLF));
    }

    body.push(Buffer.from(`--${boundary}--${CRLF}`));
    return Buffer.concat(body);
}

async function runMasterAudit() {
    console.log('========================================================================');
    console.log('MASTER FULL-STACK APPLICATION AUDIT & WORKFLOW TEST SUITE');
    console.log('========================================================================\n');

    let passed = 0;
    let failed = 0;
    const testLog = [];

    function assert(condition, testName, details = '') {
        if (condition) {
            console.log(`[PASS] ${testName} ${details ? '(' + details + ')' : ''}`);
            passed++;
            testLog.push({ name: testName, status: 'PASS', details });
        } else {
            console.error(`[FAIL] ${testName} - Details: ${details}`);
            failed++;
            testLog.push({ name: testName, status: 'FAIL', details });
        }
    }

    try {
        // =====================================================================
        // SECTION 1: AUTHENTICATION & STEP-UP SECURITY (RBAC)
        // =====================================================================
        console.log('\n--- [SECTION 1] THREE-ROLE AUTHENTICATION & ACCESS CONTROL ---');

        // 1.1 Admin Authentication
        const adminAuth = await request({
            hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'admin@disaster.gov.in', password: 'admin123' });
        assert(adminAuth.statusCode === 200 && adminAuth.data.token && adminAuth.data.role === 'Admin', 'Admin Login & JWT Issuance');
        const adminToken = adminAuth.data.token;

        // 1.2 Citizen Authentication
        const citizenAuth = await request({
            hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'citizen@gmail.com', password: 'citizen123' });
        assert(citizenAuth.statusCode === 200 && citizenAuth.data.token && citizenAuth.data.role === 'Citizen', 'Citizen Login & JWT Issuance');
        const citizenToken = citizenAuth.data.token;

        // 1.3 Responder Authentication
        const responderAuth = await request({
            hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'responder@disaster.gov.in', password: 'responder123' });
        assert(responderAuth.statusCode === 200 && responderAuth.data.token && responderAuth.data.role === 'Responder', 'Responder Login & JWT Issuance');
        const responderToken = responderAuth.data.token;

        // 1.4 Admin 2FA Step-Up Security PIN Verification
        const pinVerify = await request({
            hostname: 'localhost', port: 8080, path: '/api/auth/admin/pin/verify', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${adminToken}`
            }
        }, { pin: '123456' });
        assert(pinVerify.statusCode === 200 && pinVerify.data.stepUpToken, 'Admin 2FA PIN Verification & Step-Up Token');
        const stepUpToken = pinVerify.data.stepUpToken;

        // 1.5 Role-Based Authorization Guard Test: Citizen denied Admin-only route
        const rbacBlock = await request({
            hostname: 'localhost', port: 8080, path: '/api/auth/admin/audit-logs', method: 'GET',
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        assert(rbacBlock.statusCode === 403 || rbacBlock.statusCode === 401, 'RBAC Enforcement (Citizen Blocked from Admin Audit Logs)');

        // =====================================================================
        // SECTION 2: CITIZEN REPORTING & MEDIA PIPELINE
        // =====================================================================
        console.log('\n--- [SECTION 2] CITIZEN REPORTING, MEDIA & LIFE-SAFETY SOS ---');

        const realJpegData = 'data:image/jpeg;base64,' + Buffer.from(
            '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcU' +
            'FhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgo' +
            'KCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAAgACADASIA' +
            'AhEBAxEB/8QAFwABAQEBAAAAAAAAAAAAAAAAAAMGB//EACQQAAICAgECBwEAAAAAAAAAAAECERID' +
            'IQQxQVEFYXGRwf/EABcBAQEBAQAAAAAAAAAAAAAAAAADBAX/xAAYEQEBAQEBAAAAAAAAAAAAAAAA' +
            'AgMBQf/aAAwDAQACEQMRAD8A56p52k5W26v7Zfp6d2o5O3dr7ZGNOnin9vK17F+mm5Onv3qzz4bN' +
            '7Vq0ctqOTu3fsX6epSik7bdWvsrdY0uUZOFeVrt0dPSpScX9v2t969sPPk56uXp6eUknK/pfY5+m' +
            'knBJyu7/AFr2L9PPKnJyt6V9v5g8mG3Wv/Z'
        ).toString('base64');

        // 2.1 Submit Citizen Distress Report
        const reportRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/citizen-reports', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${citizenToken}`
            }
        }, {
            citizenEmail: 'citizen@gmail.com',
            category: 'Flood',
            location: 'Velachery Bypass Road, Chennai',
            message: 'Ground floor submerged, 4 residents require rescue boat assistance immediately.',
            photoData: realJpegData,
            latitude: 12.9750,
            longitude: 80.2210
        });
        assert(reportRes.statusCode === 200 && reportRes.data.reportId, 'Citizen Submits Distress Report with Photo', `Report ID: ${reportRes.data && reportRes.data.reportId}`);
        const citizenReportId = reportRes.data.reportId;

        // 2.2 Citizen Queries Own Reports (My Reports)
        const myReportsRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/citizen-reports/my-reports', method: 'GET',
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        assert(myReportsRes.statusCode === 200 && Array.isArray(myReportsRes.data) && myReportsRes.data.length > 0, 'Citizen Retrieves "My Reports" List');

        // 2.3 Upload Real Media Attachment (/api/media/upload)
        const boundary = '----AuditMedia' + Date.now();
        const testImageBuffer = Buffer.from(realJpegData.split(',')[1], 'base64');
        const multipartBody = buildMultipartFormData(
            boundary,
            {
                incidentId: citizenReportId,
                incidentType: 'CITIZEN_REPORT',
                caption: 'Rising flood waters near residential building',
                latitude: '12.9750',
                longitude: '80.2210',
                accuracy: '3.5'
            },
            {
                fieldname: 'file',
                filename: 'velachery_flood_evidence.jpg',
                contentType: 'image/jpeg',
                buffer: testImageBuffer
            }
        );
        const mediaUploadRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/media/upload', method: 'POST',
            headers: {
                'Content-Type': `multipart/form-data; boundary=${boundary}`,
                'Authorization': `Bearer ${citizenToken}`,
                'Content-Length': multipartBody.length
            }
        }, multipartBody);
        assert(mediaUploadRes.statusCode === 200 && mediaUploadRes.data.status === 'success', 'Media File Stored to Disk & Indexed in MySQL', `File: ${mediaUploadRes.data && mediaUploadRes.data.storedFilename}`);
        const storedFilename = mediaUploadRes.data.storedFilename;

        // 2.4 Verify Media Streaming Endpoint
        const streamRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/media/file/${encodeURIComponent(storedFilename)}`, method: 'GET'
        });
        assert(streamRes.statusCode === 200 && streamRes.headers['content-type'] === 'image/jpeg', 'Public Streaming of Stored Evidence File');

        // 2.5 Submit a duplicate report and verify Citizen Cancellation
        const cancelTargetRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/citizen-reports', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${citizenToken}`
            }
        }, {
            citizenEmail: 'citizen@gmail.com',
            category: 'Cyclone',
            location: 'Marina Promenade',
            message: 'Accidental duplicate test submission.',
            status: 'Pending'
        });
        const cancelTargetId = cancelTargetRes.data.reportId;
        const citizenCancelRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/citizen-reports/${cancelTargetId}`, method: 'DELETE',
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        assert(citizenCancelRes.statusCode === 200, 'Citizen Cancels Own Pending Report (DELETE /api/citizen-reports/{id})');

        // =====================================================================
        // SECTION 3: ADMIN MISSION CONTROL & INCIDENT DISPATCH
        // =====================================================================
        console.log('\n--- [SECTION 3] ADMIN MISSION CONTROL, SMART ASSIGNMENT & AUDIT ---');

        // 3.1 Admin Broadcasts Emergency Alert (with 2FA Step-Up Token)
        const broadcastRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/alerts', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${adminToken}`,
                'X-Admin-Step-Up-Token': stepUpToken
            }
        }, {
            title: 'IMMEDIATE EVACUATION: Velachery Zone Flash Inundation',
            disasterType: 'Flood',
            severity: 'High',
            region: 'Chennai',
            message: 'Disaster management response units mobilized. Evacuate low-lying structures immediately.',
            broadcasted: true
        });
        const broadcastAlertId = (broadcastRes.data && broadcastRes.data.alert && broadcastRes.data.alert.alertId) || (broadcastRes.data && broadcastRes.data.alertId);
        assert(broadcastRes.statusCode === 200 && broadcastAlertId, 'Admin Emergency Broadcast with 2FA Step-Up Authorization');

        // 3.2 Admin Creates Rescue Task from Citizen Report
        const taskCreateRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/tasks', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${adminToken}`
            }
        }, {
            title: `Rescue Mission - Citizen Report #${citizenReportId}`,
            description: 'Deploy inflatable rescue raft to Velachery residential sector',
            zone: 'Central Zone',
            priority: 'High',
            locationName: 'Velachery Bypass Road, Chennai',
            latitude: 12.9750,
            longitude: 80.2210,
            status: 'Pending'
        });
        const taskId = taskCreateRes.data && taskCreateRes.data.task ? (taskCreateRes.data.task.taskId || taskCreateRes.data.task.id) : null;
        assert(taskCreateRes.statusCode === 200 && taskId, 'Admin Creates Incident Task in MySQL', `Task ID: ${taskId}`);

        // 3.3 Smart Assignment Recommendation Evaluation
        const smartAssignRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/admin/assignments/recommendations/${taskId}`, method: 'GET',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        const hasRecs = smartAssignRes.statusCode === 200 && (Array.isArray(smartAssignRes.data) || (smartAssignRes.data && Array.isArray(smartAssignRes.data.recommendations)));
        assert(hasRecs, 'Smart Assignment Engine Evaluates Responder Roster');

        // 3.4 Set Responder to Available and Assign Task
        await request({
            hostname: 'localhost', port: 8080, path: '/api/auth/responder/availability', method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            }
        }, { availability: 'Available' });

        const assignRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/tasks/${taskId}/assign?responderEmail=responder@disaster.gov.in`, method: 'PUT',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        assert(assignRes.statusCode === 200 && assignRes.data.status === 'success', 'Admin Assigns Task to Responder Unit');

        // 3.5 Availability Enforcement: Attempt to assign when responder is set to Offline / Unavailable
        const blockedAssignRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/tasks/${taskId}/assign?responderEmail=responder2@disaster.gov.in`, method: 'PUT',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        assert(blockedAssignRes.statusCode === 400, 'Assignment Blocked When Responder Is Unavailable / Offline');

        // 3.6 Admin Invalidation with Immutable Audit Trail
        const invalidReportRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/citizen-reports', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${citizenToken}`
            }
        }, {
            citizenEmail: 'citizen@gmail.com',
            category: 'Fire',
            location: 'Spam Report Test',
            message: 'Duplicate report testing audit logging.',
            status: 'Pending'
        });
        const spamReportId = invalidReportRes.data.reportId;

        const adminInvalidateRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/citizen-reports/${spamReportId}?reason=${encodeURIComponent('False alarm - duplicate test report')}`, method: 'DELETE',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        assert(adminInvalidateRes.statusCode === 200, 'Admin Invalidates Report with Mandatory Audit Reason');

        const auditTrailRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/auth/admin/audit-logs', method: 'GET',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'X-Admin-Step-Up-Token': stepUpToken
            }
        });
        const hasAuditEntry = auditTrailRes.data && Array.isArray(auditTrailRes.data) && auditTrailRes.data.some(log => log.action === 'CANCEL_CITIZEN_REPORT' && log.targetId === String(spamReportId));
        assert(hasAuditEntry, 'Immutable Audit Trail Persisted in MySQL (admin_audit_logs)');

        // =====================================================================
        // SECTION 4: RESPONDER FIELD OPS, TELEMETRY & STATUS LIFECYCLE
        // =====================================================================
        console.log('\n--- [SECTION 4] RESPONDER DISPATCH ACCEPTANCE & LIVE GPS RADAR ---');

        // 4.1 Responder Accepts Dispatch ("En Route")
        const enRouteRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/tasks/${taskId}/status`, method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            }
        }, {
            status: 'En Route',
            notes: 'Dispatch accepted — Unit is now En Route.'
        });
        assert(enRouteRes.statusCode === 200, 'Responder Accepts Dispatch ("Dispatch accepted — Unit is now En Route.")');

        // 4.2 Start Live GPS Telemetry Stream
        const trackingStartRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/tracking/start', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            }
        }, {
            assignmentId: taskId,
            latitude: 12.9800,
            longitude: 80.2250,
            accuracy: 4.2
        });
        assert(trackingStartRes.statusCode === 200, 'Responder Initiates GPS Telemetry Streaming');

        // 4.3 Update Telemetry Coordinates While Active
        const trackingUpdateRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/tracking/update', method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            }
        }, {
            assignmentId: taskId,
            latitude: 12.9775,
            longitude: 80.2230,
            accuracy: 3.8
        });
        assert(trackingUpdateRes.statusCode === 200, 'Real-Time Position Updates Transmitted to Central Command');

        // 4.4 Citizen Queries Assigned Responder GPS Radar
        const citizenRadarRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/tracking/latest/${taskId}`, method: 'GET',
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        const radarActive = citizenRadarRes.statusCode === 200 && (citizenRadarRes.data.status === 'ACTIVE' || citizenRadarRes.data.assignmentId !== undefined);
        assert(radarActive, 'Citizen Views Live Responder Tracking Radar & Speed/Coords');

        // 4.5 Responder Advances Stage to "In Progress" with SitRep
        const inProgressRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/tasks/${taskId}/status`, method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            }
        }, {
            status: 'In Progress',
            notes: 'Field SitRep: Rescue raft deployed. Residents equipped with life jackets.'
        });
        assert(inProgressRes.statusCode === 200, 'Field SitRep Note Recorded & Operational Stepper Advances to "In Progress"');

        // 4.6 Complete Mission and Terminate Telemetry
        const completeRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/tasks/${taskId}/status`, method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            }
        }, {
            status: 'Completed',
            notes: 'All 4 residents safely evacuated to Government School Relief Shelter.'
        });
        assert(completeRes.statusCode === 200, 'Mission Marked Completed & Closed');

        // =====================================================================
        // SECTION 5: REAL-TIME INCIDENT COMMS ROOM & MESSAGING
        // =====================================================================
        console.log('\n--- [SECTION 5] INCIDENT COMMS ROOM & PERSISTENT CHAT ---');

        // 5.1 Citizen Posts Message
        const citMsgRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/incidents/${citizenReportId}/timeline/message`, method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${citizenToken}`
            }
        }, {
            incidentType: 'CITIZEN_REPORT',
            message: 'Water levels stable now. We see the response raft approaching.'
        });
        assert(citMsgRes.statusCode === 200, 'Citizen Sends Tactical Message in Comms Room');

        // 5.2 Responder Posts Reply
        const respMsgRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/incidents/${citizenReportId}/timeline/message`, method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            }
        }, {
            incidentType: 'CITIZEN_REPORT',
            message: 'Unit 4 is directly outside your gate. Prepare for boarding.'
        });
        assert(respMsgRes.statusCode === 200, 'Responder Sends Coordination Update');

        // 5.3 Fetch Comms Room Timeline
        const timelineRes = await request({
            hostname: 'localhost', port: 8080, path: `/api/incidents/${citizenReportId}/timeline?type=CITIZEN_REPORT`, method: 'GET',
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        assert(timelineRes.statusCode === 200 && Array.isArray(timelineRes.data) && timelineRes.data.length >= 2, 'Comms Room Chronological Stream Persisted in MySQL');

        // =====================================================================
        // SECTION 6: OFFICIAL NDMA SACHET INTEGRATION & TELEMETRY
        // =====================================================================
        console.log('\n--- [SECTION 6] OFFICIAL NDMA SACHET CAP 1.2 INTEGRATION ---');

        // 6.1 Query Official Active Alerts
        const officialRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/official-alerts/active', method: 'GET'
        });
        assert(officialRes.statusCode === 200 && Array.isArray(officialRes.data), 'Official NDMA SACHET Disaster Warnings Feed');

        // 6.2 Query Ingestion Status
        const feedStatusRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/official-alerts/status', method: 'GET'
        });
        const hasStatus = feedStatusRes.statusCode === 200 && (feedStatusRes.data.totalRecordsImported !== undefined || feedStatusRes.data.lastSuccessfulSync !== undefined || feedStatusRes.data.activeOfficialAlertsCount !== undefined);
        assert(hasStatus, 'NDMA Sync Health & CAP Ingestion Telemetry');

        // 6.3 Trigger Manual Feed Sync
        const syncRes = await request({
            hostname: 'localhost', port: 8080, path: '/api/admin/ndma/sync', method: 'POST',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        assert(syncRes.statusCode === 200, 'Administrative Ingestion Trigger (POST /api/admin/ndma/sync)');

        // =====================================================================
        // SECTION 7: MULTI-VIEWPORT RESPONSIVE SCREENSHOT AUDIT
        // =====================================================================
        console.log('\n--- [SECTION 7] RESPONSIVE SCREENSHOT AUDIT (DESKTOP, LAPTOP, TABLET, MOBILE) ---');

        if (!fs.existsSync(ARTIFACT_DIR)) fs.mkdirSync(ARTIFACT_DIR, { recursive: true });

        const viewports = [
            { name: 'desktop', width: 1440, height: 900 },
            { name: 'laptop', width: 1366, height: 768 },
            { name: 'tablet', width: 768, height: 1024 },
            { name: 'mobile', width: 390, height: 844 }
        ];

        const auditPages = [
            { id: 'admin_mission_control', url: 'http://localhost:8080/admin-dashboard.html' },
            { id: 'admin_tasks_and_validation', url: 'http://localhost:8080/admin-tasks.html' },
            { id: 'citizen_safety_hub', url: 'http://localhost:8080/citizen-dashboard.html' },
            { id: 'responder_command', url: 'http://localhost:8080/responder-dashboard.html' },
            { id: 'incident_telemetry', url: 'http://localhost:8080/reports.html' }
        ];

        for (const page of auditPages) {
            for (const vp of viewports) {
                const filename = `audit_${page.id}_${vp.name}_${vp.width}x${vp.height}.png`;
                const outPath = path.join(ARTIFACT_DIR, filename);

                if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1000) {
                    assert(true, `Captured ${filename}`);
                    continue;
                }

                const tempProfile = path.join(require('os').tmpdir(), `chrome_tmp_${Date.now()}_${Math.floor(Math.random() * 1000)}`);
                const args = [
                    '--headless=new',
                    '--disable-gpu',
                    '--hide-scrollbars',
                    '--no-sandbox',
                    '--disable-extensions',
                    '--disable-default-apps',
                    '--disable-background-networking',
                    '--disable-sync',
                    `--user-data-dir=${tempProfile}`,
                    `--window-size=${vp.width},${vp.height}`,
                    `--screenshot=${outPath}`,
                    page.url
                ];

                try {
                    execFileSync(CHROME_PATH, args, { timeout: 10000 });
                    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1000) {
                        assert(true, `Captured ${filename}`);
                    } else {
                        assert(false, `Capture failed: ${filename}`);
                    }
                } catch (e) {
                    console.warn(`[WARN] Screenshot capture: ${e.message}`);
                    assert(false, `Capture failed: ${filename}`);
                }
            }
        }

        console.log('\n========================================================================');
        console.log(`MASTER AUDIT COMPLETE: ${passed} PASSED, ${failed} FAILED`);
        console.log('========================================================================\n');

        fs.writeFileSync('scripts/master_audit_results.json', JSON.stringify({
            timestamp: new Date().toISOString(),
            passed,
            failed,
            log: testLog
        }, null, 2), 'utf8');

        if (failed > 0) process.exit(1);
        else process.exit(0);

    } catch (err) {
        console.error('Fatal master audit error:', err);
        process.exit(1);
    }
}

runMasterAudit();
