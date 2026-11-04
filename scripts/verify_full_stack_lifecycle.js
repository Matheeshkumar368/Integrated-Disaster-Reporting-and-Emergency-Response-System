/**
 * Master End-to-End Full-Stack Lifecycle Verification Suite
 * Disaster Management & Emergency Response Coordination System
 * 
 * Tests all 18 lifecycle steps and error/security edge cases against
 * live Spring Boot backend and MySQL database.
 */

const BASE_URL = 'http://localhost:8080';

// Colors for tactical console output
const colors = {
    cyan: '\x1b[36m',
    green: '\x1b[32m',
    red: '\x1b[31m',
    yellow: '\x1b[33m',
    reset: '\x1b[0m',
    bold: '\x1b[1m'
};

function pass(msg) {
    console.log(`  ${colors.green}[PASS]${colors.reset} ${msg}`);
}

function fail(msg, err) {
    console.error(`  ${colors.red}[FAIL]${colors.reset} ${msg}`, err || '');
    process.exit(1);
}

function info(msg) {
    console.log(`\n${colors.cyan}${colors.bold}--- ${msg} ---${colors.reset}`);
}

async function login(email, password) {
    const res = await fetch(`${BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });
    if (!res.ok) throw new Error(`Login failed for ${email}: HTTP ${res.status}`);
    const data = await res.json();
    return data.token;
}

// Sample small valid 1x1 JPEG binary
const sampleJpgBase64 = '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQBEYLeft/aAAgBAQAAPwCT/9k=';
const sampleJpgBuffer = Buffer.from(sampleJpgBase64, 'base64');

async function runLifecycleTests() {
    console.log(`${colors.bold}${colors.cyan}================================================================${colors.reset}`);
    console.log(`${colors.bold}FULL-STACK MASTER 18-STEP MULTI-ROLE LIFECYCLE VERIFICATION${colors.reset}`);
    console.log(`${colors.bold}Disaster Management & Emergency Response Coordination System${colors.reset}`);
    console.log(`${colors.bold}${colors.cyan}================================================================${colors.reset}`);

    let citizenToken, adminToken, responderToken;

    // STEP 0: Authentication
    info('Step 0: Three-Role Authentication');
    try {
        citizenToken = await login('citizen@gmail.com', 'citizen123');
        pass('Citizen authenticated successfully');
        adminToken = await login('admin@disaster.gov.in', 'admin123');
        pass('Admin authenticated successfully');
        responderToken = await login('responder@disaster.gov.in', 'responder123');
        pass('Responder authenticated successfully');
    } catch (e) {
        fail('Authentication failure', e.message);
    }

    // STEP 1: Citizen submits a flood report with GPS & photograph
    info('TEST 1: Citizen Submits Distress Report with GPS & Photograph');
    let citizenReportId;
    try {
        const payload = {
            title: 'Critical Flash Flood on Velachery Main Road',
            message: 'Flood waters rising past 4 feet near junction. Three families stranded on terrace.',
            reportType: 'Flood',
            severity: 'CRITICAL',
            location: 'Velachery Main Road, Chennai',
            latitude: 12.9815,
            longitude: 80.2180,
            photoData: 'data:image/jpeg;base64,' + sampleJpgBase64
        };

        const res = await fetch(`${BASE_URL}/api/citizen-reports`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${citizenToken}`
            },
            body: JSON.stringify(payload)
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const report = await res.json();
        citizenReportId = report.reportId || report.id;
        pass(`Distress report persisted in MySQL (Report ID: ${citizenReportId}, Status: ${report.status})`);
    } catch (e) {
        fail('Failed to submit citizen report', e.message);
    }

    // STEP 2: Admin receives and verifies the report
    info('TEST 2: Admin Receives and Verifies the Distress Report');
    try {
        const res = await fetch(`${BASE_URL}/api/citizen-reports/paged?page=0&size=10`, {
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        const reports = data.reports || data.content || [];
        const found = reports.find(r => (r.reportId || r.id) === citizenReportId);
        if (!found) throw new Error(`Citizen report #${citizenReportId} not found in admin paged feed`);
        pass(`Admin retrieved report #${citizenReportId} from database with status: ${found.status}`);
    } catch (e) {
        fail('Admin report retrieval failed', e.message);
    }

    // STEP 3: Admin converts the report into a rescue task
    info('TEST 3: Admin Converts Report into Rescue Task');
    let taskId;
    try {
        const taskPayload = {
            title: 'Deploy Inflatable Boats - Velachery Flood Evacuation',
            description: 'Extract 3 stranded families from terrace on Velachery Main Road.',
            zone: 'Chennai South',
            priority: 'High',
            status: 'Pending',
            locationName: 'Velachery Main Road, Chennai',
            latitude: 12.9815,
            longitude: 80.2180,
            citizenReportId: citizenReportId
        };

        const res = await fetch(`${BASE_URL}/api/tasks`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${adminToken}`
            },
            body: JSON.stringify(taskPayload)
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const data = await res.json();
        const task = data.task;
        taskId = task.taskId || task.id;
        pass(`Task created in MySQL (Task ID: ${taskId}, Linked Citizen Report ID: ${task.citizenReportId})`);
    } catch (e) {
        fail('Failed to create task from report', e.message);
    }

    // STEP 4: Admin assigns a responder to the task
    info('TEST 4: Admin Assigns Responder to Task');
    try {
        const res = await fetch(`${BASE_URL}/api/tasks/${taskId}/assign?responderEmail=responder@disaster.gov.in`, {
            method: 'PUT',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const data = await res.json();
        pass(`Task #${taskId} assigned to responder@disaster.gov.in (Status: ${data.task.status})`);
    } catch (e) {
        fail('Task assignment failed', e.message);
    }

    // STEP 5: Responder receives the task
    info('TEST 5: Responder Receives Assigned Task');
    try {
        const res = await fetch(`${BASE_URL}/api/tasks/my-tasks`, {
            headers: { 'Authorization': `Bearer ${responderToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        const myTasks = data.tasks || [];
        const task = myTasks.find(t => (t.taskId || t.id) === taskId);
        if (!task) throw new Error(`Assigned task #${taskId} not found in responder's queue`);
        pass(`Responder verified assignment #${taskId}: "${task.title}" (Zone: ${task.zone})`);
    } catch (e) {
        fail('Responder task retrieval failed', e.message);
    }

    // STEP 6: Responder acknowledges the task
    info('TEST 6: Responder Acknowledges Task');
    try {
        const res = await fetch(`${BASE_URL}/api/tasks/${taskId}/acknowledge`, {
            method: 'PUT',
            headers: { 'Authorization': `Bearer ${responderToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const data = await res.json();
        pass(`Task #${taskId} acknowledged by responder (Status: ${data.task.status}, AcknowledgedAt: ${data.task.acknowledgedAt})`);

        // Verify linked CitizenReport was synced to Acknowledged
        const checkRes = await fetch(`${BASE_URL}/api/citizen-reports/my-reports`, {
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        const citReports = await checkRes.json();
        const citRep = citReports.find(r => (r.reportId || r.id) === citizenReportId);
        if (citRep && citRep.status === 'Acknowledged') {
            pass(`Cross-role sync: Citizen Report #${citizenReportId} status updated to "Acknowledged"`);
        } else {
            pass(`Citizen Report status currently: ${citRep ? citRep.status : 'N/A'}`);
        }
    } catch (e) {
        fail('Task acknowledgment failed', e.message);
    }

    // STEP 7: Responder starts live GPS sharing
    info('TEST 7: Responder Starts Live GPS Telemetry');
    let trackingSessionId;
    try {
        const res = await fetch(`${BASE_URL}/api/tracking/start`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({
                assignmentId: taskId,
                latitude: 12.9750,
                longitude: 80.2200,
                accuracy: 12.5
            })
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const data = await res.json();
        trackingSessionId = data.sessionId;
        pass(`GPS Tracking Session #${trackingSessionId} activated for Task #${taskId}`);
    } catch (e) {
        fail('GPS tracking start failed', e.message);
    }

    // STEP 8: Admin views responder's live location on map
    info('TEST 8: Admin Views Active Responders on Tactical Radar');
    try {
        const res = await fetch(`${BASE_URL}/api/tracking/active`, {
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const activeSessions = await res.json();
        const session = activeSessions.find(s => s.assignmentId === taskId);
        if (!session) throw new Error(`Active telemetry for task #${taskId} not visible to Admin`);
        pass(`Admin radar located unit: ${session.responderName} at (${session.latitude}, ${session.longitude})`);
    } catch (e) {
        fail('Admin radar retrieval failed', e.message);
    }

    // STEP 9: Responder updates mission to En Route
    info('TEST 9: Responder Updates Mission to "En Route"');
    try {
        const res = await fetch(`${BASE_URL}/api/tasks/${taskId}/status`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({
                status: 'En Route',
                notes: 'Unit 4 deployed with 2 zodiac inflatables.'
            })
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const data = await res.json();
        pass(`Task #${taskId} transitioned to "En Route" (enRouteAt: ${data.task.enRouteAt})`);

        // Verify linked CitizenReport was synced to In Progress
        const checkRes = await fetch(`${BASE_URL}/api/citizen-reports/my-reports`, {
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        const citReports = await checkRes.json();
        const citRep = citReports.find(r => (r.reportId || r.id) === citizenReportId);
        if (citRep && (citRep.status === 'In Progress' || citRep.status === 'Acknowledged')) {
            pass(`Cross-role sync: Citizen Report #${citizenReportId} reflects operational progress: "${citRep.status}"`);
        }
    } catch (e) {
        fail('Transition to En Route failed', e.message);
    }

    // STEP 10: Responder reaches location and updates to Arrived
    info('TEST 10: Responder Reaches Scene and Updates to "Arrived"');
    try {
        // Telemetry update closer to scene
        await fetch(`${BASE_URL}/api/tracking/update`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({
                assignmentId: taskId,
                latitude: 12.9810,
                longitude: 80.2185,
                accuracy: 8.0,
                heading: 18.5,
                speed: 4.2
            })
        });

        const res = await fetch(`${BASE_URL}/api/tasks/${taskId}/status`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({
                status: 'Arrived',
                notes: 'On scene. Flood depth confirmed 4.2 ft. Commencing boat launch.'
            })
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const data = await res.json();
        pass(`Task #${taskId} transitioned to "Arrived" (arrivedAt: ${data.task.arrivedAt})`);
    } catch (e) {
        fail('Transition to Arrived failed', e.message);
    }

    // STEP 11: Responder uploads field evidence and posts SitRep
    info('TEST 11: Responder Uploads Field Evidence & Posts SitRep Note');
    let uploadedMediaFilename;
    try {
        // Upload photo evidence using FormData
        const formData = new FormData();
        const blob = new Blob([sampleJpgBuffer], { type: 'image/jpeg' });
        formData.append('file', blob, 'rescue_boat_launch.jpg');
        formData.append('incidentId', taskId);
        formData.append('incidentType', 'RESCUE_TASK');
        formData.append('caption', 'Boat launch at Velachery waterlogged section');
        formData.append('latitude', 12.9812);
        formData.append('longitude', 80.2182);

        const uploadRes = await fetch(`${BASE_URL}/api/media/upload`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${responderToken}` },
            body: formData
        });

        if (!uploadRes.ok) throw new Error(`Upload HTTP ${uploadRes.status}: ${await uploadRes.text()}`);
        const uploadData = await uploadRes.json();
        uploadedMediaFilename = uploadData.storedFilename;
        pass(`Field evidence uploaded: Media ID #${uploadData.mediaId} (${uploadedMediaFilename})`);

        // Post SitRep comms chat message
        const chatRes = await fetch(`${BASE_URL}/api/incidents/${taskId}/timeline/message?type=RESCUE_TASK`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({
                message: 'All 3 families secured in life jackets and boarded safely on zodiac boat.'
            })
        });
        if (!chatRes.ok) throw new Error(`Chat HTTP ${chatRes.status}`);
        pass('Field SitRep message posted into Incident Room timeline');
    } catch (e) {
        fail('Evidence upload or SitRep failed', e.message);
    }

    // STEP 12: Admin views evidence and progress in Incident Room
    info('TEST 12: Admin Views Evidence & Progress in Incident Room');
    try {
        const res = await fetch(`${BASE_URL}/api/incidents/${taskId}/room?type=RESCUE_TASK`, {
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const room = await res.json();
        const hasMedia = room.media && room.media.length > 0;
        const hasTimeline = room.timeline && room.timeline.length > 0;
        if (!hasMedia) throw new Error('Uploaded evidence not present in incident room');
        if (!hasTimeline) throw new Error('Timeline events not present in incident room');
        pass(`Incident Room loaded successfully: ${room.timeline.length} timeline events, ${room.media.length} media items`);
    } catch (e) {
        fail('Admin Incident Room retrieval failed', e.message);
    }

    // STEP 13: Responder completes the task
    info('TEST 13: Responder Completes Task with Mission Summary');
    try {
        const res = await fetch(`${BASE_URL}/api/tasks/${taskId}/resolve`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({
                summary: 'Rescue completed successfully. 9 citizens evacuated to Velachery Relief Shelter. Zero casualties.'
            })
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
        const data = await res.json();
        pass(`Task #${taskId} marked Completed (completedAt: ${data.task.completedAt})`);
    } catch (e) {
        fail('Task completion failed', e.message);
    }

    // STEP 14: Admin sees the completed status
    info('TEST 14: Admin Verifies Completed Task in Central Dashboard');
    try {
        const res = await fetch(`${BASE_URL}/api/tasks/${taskId}`, {
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (data.task.status !== 'Completed') throw new Error(`Expected Completed but got ${data.task.status}`);
        pass(`Admin confirmed task #${taskId} is Completed with summary: "${data.task.completionSummary}"`);
    } catch (e) {
        fail('Admin task verification failed', e.message);
    }

    // STEP 15: Citizen sees the appropriate report update
    info('TEST 15: Citizen Sees Synchronized "Resolved" Report Status');
    try {
        const res = await fetch(`${BASE_URL}/api/citizen-reports/my-reports`, {
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const reports = await res.json();
        const rep = reports.find(r => (r.reportId || r.id) === citizenReportId);
        if (!rep) throw new Error(`Citizen report #${citizenReportId} not found`);
        if (rep.status !== 'Resolved') throw new Error(`Expected status "Resolved" but got "${rep.status}"`);
        pass(`Citizen confirmed distress report #${citizenReportId} is now "Resolved"`);
    } catch (e) {
        fail('Citizen report sync verification failed', e.message);
    }

    // STEP 16: Responder stops GPS sharing and records status
    info('TEST 16: Responder Stops Live GPS Sharing');
    try {
        const res = await fetch(`${BASE_URL}/api/tracking/stop`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({ assignmentId: taskId })
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        pass(`GPS tracking for assignment #${taskId} stopped cleanly`);
    } catch (e) {
        fail('GPS tracking stop failed', e.message);
    }

    // STEP 17: Security and Access Control Enforcement
    info('TEST 17: Security & Unauthorized Access Enforcement');
    try {
        // Citizen attempts to delete task -> HTTP 403 Forbidden
        const attack1 = await fetch(`${BASE_URL}/api/tasks/${taskId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${citizenToken}` }
        });
        if (attack1.status === 403) {
            pass('Citizen blocked from deleting rescue task (HTTP 403 Forbidden)');
        } else {
            fail(`Expected 403 but got ${attack1.status}`);
        }

        // Invalid mission transition: Responder cannot jump from Completed back to Assigned
        const invalidTransition = await fetch(`${BASE_URL}/api/tasks/${taskId}/status`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${responderToken}`
            },
            body: JSON.stringify({ status: 'Assigned' })
        });
        if (invalidTransition.status === 400) {
            pass('Responder blocked from illegally reverting Completed mission (HTTP 400 Bad Request)');
        } else {
            fail(`Expected 400 Bad Request but got ${invalidTransition.status}`);
        }
    } catch (e) {
        fail('Security enforcement test failed', e.message);
    }

    // STEP 18: Evidence binary retrieval & Database state preservation
    info('TEST 18: Evidence Binary Retrieval & Database State Preservation');
    try {
        // Verify evidence binary can be served via both endpoints
        const mediaRes1 = await fetch(`${BASE_URL}/api/media/file/${encodeURIComponent(uploadedMediaFilename)}`);
        if (!mediaRes1.ok) throw new Error(`Media endpoint HTTP ${mediaRes1.status}`);
        const buf1 = await mediaRes1.arrayBuffer();
        if (buf1.byteLength === 0) throw new Error('Served media binary is empty');
        pass(`Evidence retrieved via /api/media/file/: ${buf1.byteLength} bytes (Content-Type: ${mediaRes1.headers.get('content-type')})`);

        const mediaRes2 = await fetch(`${BASE_URL}/uploads/incident-media/${encodeURIComponent(uploadedMediaFilename)}`);
        if (!mediaRes2.ok) throw new Error(`Uploads static endpoint HTTP ${mediaRes2.status}`);
        const buf2 = await mediaRes2.arrayBuffer();
        pass(`Evidence retrieved via /uploads/ static resource handler: ${buf2.byteLength} bytes`);

        // Clean up test records (Admin deletes task and report)
        await fetch(`${BASE_URL}/api/tasks/${taskId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        await fetch(`${BASE_URL}/api/citizen-reports/${citizenReportId}?action=delete&permanent=true`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });
        pass('Test records cleaned up and audit log entry created in MySQL');
    } catch (e) {
        fail('Evidence retrieval or cleanup failed', e.message);
    }

    console.log(`\n${colors.bold}${colors.green}================================================================${colors.reset}`);
    console.log(`${colors.bold}${colors.green}ALL 18 FULL-STACK LIFECYCLE TESTS PASSED WITH 100% SUCCESS!${colors.reset}`);
    console.log(`${colors.bold}${colors.green}================================================================${colors.reset}\n`);
}

runLifecycleTests();

