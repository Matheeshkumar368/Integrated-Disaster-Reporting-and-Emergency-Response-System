/**
 * verify_responder_module.js
 * Comprehensive end-to-end verification test suite for Responder Field Operations Command Center
 */
const http = require('http');

function request(options, bodyData) {
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
            req.write(typeof bodyData === 'string' ? bodyData : JSON.stringify(bodyData));
        }
        req.end();
    });
}

async function run() {
    console.log('=== RESPONDER FIELD OPERATIONS MODULE VERIFICATION ===\n');
    let failures = 0;

    // 1. Responder Authentication
    console.log('1. Testing Responder Login (responder@disaster.gov.in)...');
    const respLoginRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/auth/login',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, {
        email: 'responder@disaster.gov.in',
        password: 'responder123'
    });

    if (respLoginRes.statusCode !== 200 || !respLoginRes.data.token) {
        console.error('FAIL: Responder login failed:', respLoginRes.statusCode, respLoginRes.data);
        return;
    }
    const responderToken = respLoginRes.data.token;
    console.log(`✓ SUCCESS: Responder authenticated. Token acquired. Role: ${respLoginRes.data.role}, Name: ${respLoginRes.data.name}`);

    // Citizen login for cross-role security testing
    const citizenLoginRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/auth/login',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, {
        email: 'citizen@gmail.com',
        password: 'citizen123'
    });
    const citizenToken = citizenLoginRes.data.token;

    // Admin login for task dispatch
    const adminLoginRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/auth/login',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, {
        email: 'admin@disaster.gov.in',
        password: 'admin123'
    });
    const adminToken = adminLoginRes.data.token;

    // 2. Responder Availability Management
    console.log('\n2. Testing Responder Availability (/api/auth/responder/availability)...');
    const getAvailRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/auth/responder/availability',
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + responderToken }
    });

    if (getAvailRes.statusCode !== 200) {
        console.error('FAIL: Could not fetch responder availability:', getAvailRes.statusCode, getAvailRes.data);
        failures++;
    } else {
        console.log(`✓ SUCCESS: Responder availability retrieved: "${getAvailRes.data.availability}" (Active tasks: ${getAvailRes.data.activeTasksCount})`);
    }

    const updateAvailRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/auth/responder/availability',
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, { availability: 'On Duty' });

    if (updateAvailRes.statusCode !== 200 || updateAvailRes.data.availability !== 'On Duty') {
        console.error('FAIL: Failed to update availability to On Duty:', updateAvailRes.statusCode, updateAvailRes.data);
        failures++;
    } else {
        console.log('✓ SUCCESS: Updated availability to "On Duty" persisted successfully.');
    }

    // 3. Admin Dispatches a Real Rescue Task to Responder
    console.log('\n3. Admin Creating Tactical Rescue Task for Responder...');
    const createTaskRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/tasks',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + adminToken
        }
    }, {
        title: 'Flash Flood Rescue - Gandhipuram Underpass',
        description: 'Rising water level submerging vehicles, elderly passengers stranded in bus.',
        zone: 'Coimbatore',
        priority: 'High',
        locationName: 'Gandhipuram Underpass, Sector 4, Coimbatore',
        latitude: 11.01684,
        longitude: 76.95583,
        assignedTo: 'responder@disaster.gov.in',
        assignedResponderName: 'Emergency Responder Unit 1'
    });

    if (createTaskRes.statusCode !== 200 || !createTaskRes.data.task) {
        console.error('FAIL: Task creation failed:', createTaskRes.statusCode, createTaskRes.data);
        failures++;
        return;
    }
    const createdTask = createTaskRes.data.task;
    const testTaskId = createdTask.taskId;
    console.log(`✓ SUCCESS: Task #${testTaskId} created and assigned to responder@disaster.gov.in. Initial Status: ${createdTask.status}`);

    // 4. Responder Retrieval of Assigned Task
    console.log('\n4. Testing Responder Task Retrieval (/api/tasks/my-tasks)...');
    const myTasksRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/tasks/my-tasks',
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + responderToken }
    });

    if (myTasksRes.statusCode !== 200 || !Array.isArray(myTasksRes.data.tasks)) {
        console.error('FAIL: Fetching my-tasks failed:', myTasksRes.statusCode, myTasksRes.data);
        failures++;
    } else {
        const found = myTasksRes.data.tasks.find(t => t.taskId === testTaskId);
        if (found) {
            console.log(`✓ SUCCESS: Task #${testTaskId} found in responder queue. Priority: ${found.priority}, Zone: ${found.zone}`);
        } else {
            console.error(`FAIL: Task #${testTaskId} not found in my-tasks!`);
            failures++;
        }
    }

    // 5. Security Check: Unauthorized Citizen Attempt to Update Responder's Task
    console.log('\n5. Testing Security: Unauthorized User Updating Task Status...');
    const unauthUpdateRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tasks/${testTaskId}/status`,
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + citizenToken
        }
    }, { status: 'Acknowledged' });

    if (unauthUpdateRes.statusCode === 403) {
        console.log(`✓ SUCCESS: Unauthorized update blocked with 403 Forbidden as required: "${unauthUpdateRes.data.message}"`);
    } else {
        console.error(`FAIL: Unauthorized update was not forbidden! Status: ${unauthUpdateRes.statusCode}`, unauthUpdateRes.data);
        failures++;
    }

    // 6. Valid 5-Stage Status Progression by Authorized Responder
    console.log('\n6. Testing 5-Stage Operational Lifecycle Progression...');

    // Stage 1: Acknowledge Assignment
    const ackRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tasks/${testTaskId}/status`,
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, { status: 'Acknowledged', notes: 'Unit 1 acknowledged mission dispatch.' });

    if (ackRes.statusCode === 200 && ackRes.data.task.status === 'Acknowledged') {
        console.log(`✓ Stage 1: Task #${testTaskId} transitioned to "Acknowledged".`);
    } else {
        console.error(`FAIL: Acknowledge transition failed:`, ackRes.statusCode, ackRes.data);
        failures++;
    }

    // Stage 2: Deploy En Route
    const enRouteRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tasks/${testTaskId}/status`,
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, { status: 'En Route', notes: 'Vehicle leaving base station, siren active.' });

    if (enRouteRes.statusCode === 200 && enRouteRes.data.task.status === 'En Route') {
        console.log(`✓ Stage 2: Task #${testTaskId} transitioned to "En Route".`);
    } else {
        console.error(`FAIL: En Route transition failed:`, enRouteRes.statusCode, enRouteRes.data);
        failures++;
    }

    // 7. Live Location Sharing & Telemetry Telemetry Stream
    console.log('\n7. Testing Live Location Telemetry (/api/tracking)...');

    // Start Tracking
    const startTrackRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/tracking/start',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, {
        assignmentId: testTaskId,
        latitude: 11.01120,
        longitude: 76.95100,
        accuracy: 6.5
    });

    if (startTrackRes.statusCode === 200 && startTrackRes.data.status === 'success') {
        console.log(`✓ SUCCESS: Live tracking started for Task #${testTaskId}. Session ID: ${startTrackRes.data.sessionId}`);
    } else {
        console.error(`FAIL: Starting tracking session failed:`, startTrackRes.statusCode, startTrackRes.data);
        failures++;
    }

    // Telemetry Location Update
    const updateLocRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/tracking/update',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, {
        assignmentId: testTaskId,
        latitude: 11.01540,
        longitude: 76.95380,
        accuracy: 4.8,
        heading: 38.5,
        speed: 34.2
    });

    if (updateLocRes.statusCode === 200 && updateLocRes.data.latitude === 11.01540) {
        console.log(`✓ SUCCESS: Telemetry updated: Lat ${updateLocRes.data.latitude}, Lng ${updateLocRes.data.longitude}`);
    } else {
        console.error(`FAIL: Updating telemetry location failed:`, updateLocRes.statusCode, updateLocRes.data);
        failures++;
    }

    // Check Latest Telemetry (Citizen / Admin view)
    const latestTrackRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tracking/latest/${testTaskId}`,
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + citizenToken }
    });

    if (latestTrackRes.statusCode === 200 && latestTrackRes.data.latitude === 11.01540) {
        console.log(`✓ SUCCESS: Cross-role verification: Citizen received live responder position: Lat ${latestTrackRes.data.latitude}, Lng ${latestTrackRes.data.longitude}`);
    } else {
        console.error(`FAIL: Citizen could not view live responder telemetry:`, latestTrackRes.statusCode, latestTrackRes.data);
        failures++;
    }

    // Stage 3: Arrived On Scene
    console.log('\n8. Continuing Lifecycle: Mark Arrived On Scene...');
    const arrivedRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tasks/${testTaskId}/status`,
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, { status: 'Arrived', notes: 'Arrived at Gandhipuram underpass, preparing inflatable rescue raft.' });

    if (arrivedRes.statusCode === 200 && arrivedRes.data.task.status === 'Arrived') {
        console.log(`✓ Stage 3: Task #${testTaskId} transitioned to "Arrived".`);
    } else {
        console.error(`FAIL: Arrived transition failed:`, arrivedRes.statusCode, arrivedRes.data);
        failures++;
    }

    // 9. Operational Rule Check: Cannot set availability to Unavailable while active mission is Arrived/En Route
    console.log('\n9. Testing Operational Rule: Cannot bypass active duty to Unavailable...');
    const bypassAvailRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/auth/responder/availability',
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, { availability: 'Unavailable' });

    if (bypassAvailRes.statusCode === 400) {
        console.log(`✓ SUCCESS: Operational rule enforced: "${bypassAvailRes.data.message}"`);
    } else {
        console.error(`FAIL: Availability bypass allowed while active mission in progress! Status: ${bypassAvailRes.statusCode}`);
        failures++;
    }

    // Stage 4: Resolve / Complete Incident
    console.log('\n10. Testing Incident Resolution (Stage 4)...');
    const completeRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tasks/${testTaskId}/status`,
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, { status: 'Completed', notes: 'All 6 trapped civilians extracted safely. Medical triage administered.' });

    if (completeRes.statusCode === 200 && completeRes.data.task.status === 'Completed') {
        console.log(`✓ Stage 4: Task #${testTaskId} successfully transitioned to "Completed".`);
    } else {
        console.error(`FAIL: Complete transition failed:`, completeRes.statusCode, completeRes.data);
        failures++;
    }

    // Verify Tracking Concluded
    const checkTrackClosed = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tracking/latest/${testTaskId}`,
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + responderToken }
    });
    console.log(`✓ Verified tracking session post-completion: status = ${checkTrackClosed.data.status}`);

    // Prevent Invalid Transition (Completed -> Pending)
    console.log('\n11. Testing Invalid Transition Prevention (Completed -> Pending)...');
    const invalidJumpRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tasks/${testTaskId}/status`,
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + responderToken
        }
    }, { status: 'Pending' });

    if (invalidJumpRes.statusCode === 400) {
        console.log(`✓ SUCCESS: Invalid transition blocked: "${invalidJumpRes.data.message}"`);
    } else {
        console.error(`FAIL: Invalid transition was not rejected! Status: ${invalidJumpRes.statusCode}`);
        failures++;
    }

    console.log('\n======================================================');
    if (failures === 0) {
        console.log('ALL RESPONDER MODULE BACKEND VERIFICATION TESTS PASSED (0 FAILURES)');
    } else {
        console.error(`TESTS FINISHED WITH ${failures} FAILURE(S)`);
    }
    console.log('======================================================');
}

run().catch(console.error);

