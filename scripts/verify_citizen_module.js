/**
 * verify_citizen_module.js
 * Comprehensive end-to-end testing script for Citizen Safety Hub
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
                } catch (e) {
                    // text or html
                }
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
    console.log('=== CITIZEN MODULE END-TO-END VERIFICATION ===\n');
    let failures = 0;

    // 1. Citizen Login
    console.log('1. Testing Citizen Authentication (citizen@gmail.com / citizen123)...');
    const loginRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/auth/login',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, {
        email: 'citizen@gmail.com',
        password: 'citizen123'
    });

    if (loginRes.statusCode !== 200 || !loginRes.data.token) {
        console.error('FAIL: Citizen login failed:', loginRes.statusCode, loginRes.data);
        failures++;
        return;
    }
    const citizenToken = loginRes.data.token;
    console.log('✓ SUCCESS: Citizen logged in. Token acquired. Role:', loginRes.data.role, 'Name:', loginRes.data.name);

    // 2. Fetch Active Disaster Warnings (Dual Stream: NDMA SACHET + Broadcasts)
    console.log('\n2. Testing Active Disaster Warnings Streams...');
    const officialRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/official-alerts/active',
        method: 'GET'
    });

    const broadcastRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/alerts/broadcasted',
        method: 'GET'
    });

    if (officialRes.statusCode !== 200 || !Array.isArray(officialRes.data)) {
        console.error('FAIL: Official NDMA SACHET alerts retrieval failed:', officialRes.statusCode, officialRes.data);
        failures++;
    } else {
        console.log(`✓ SUCCESS: Received ${officialRes.data.length} official NDMA SACHET alerts.`);
        if (officialRes.data.length > 0) {
            const first = officialRes.data[0];
            console.log(`  Sample NDMA Alert: [${first.severity}] ${first.hazard || first.title} (${first.areaDesc}) - Source: ${first.sender || 'NDMA'}`);
        }
    }

    if (broadcastRes.statusCode !== 200 || !Array.isArray(broadcastRes.data)) {
        console.error('FAIL: Broadcast alerts retrieval failed:', broadcastRes.statusCode, broadcastRes.data);
        failures++;
    } else {
        console.log(`✓ SUCCESS: Received ${broadcastRes.data.length} municipal emergency broadcasts.`);
        if (broadcastRes.data.length > 0) {
            const first = broadcastRes.data[0];
            console.log(`  Sample Broadcast: [${first.severity}] ${first.title} (${first.region})`);
        }
    }

    // 3. Submit Citizen Emergency Report with Structured Fields & Evidence
    console.log('\n3. Testing Citizen Incident Report Submission (/api/citizen-reports)...');
    const samplePhotoBase64 = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
    const reportPayload = {
        location: 'Sector 4, Gandhipuram Cross Cut Road, Coimbatore',
        message: '[Flood | Severity: High] Flash Flood Inundation Near Underpass\n\nStormwater drain overflowed, 3 cars submerged, elderly resident stranded.\n\n[Device GPS: 11.01684, 76.95583]',
        photoData: samplePhotoBase64
    };

    const submitRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/citizen-reports',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + citizenToken
        }
    }, reportPayload);

    let createdReportId = null;
    if (submitRes.statusCode !== 200 && submitRes.statusCode !== 201) {
        console.error('FAIL: Citizen report submission failed:', submitRes.statusCode, submitRes.data);
        failures++;
    } else {
        createdReportId = submitRes.data.reportId;
        console.log(`✓ SUCCESS: Report submitted successfully! Reference: #INC-2026-${createdReportId}`);
        console.log(`  Status: ${submitRes.data.status}, Location: ${submitRes.data.location}`);
    }

    // 4. Verify Citizen's Isolated Reports List
    console.log('\n4. Testing Citizen Isolation (/api/citizen-reports/my-reports)...');
    const myReportsRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: '/api/citizen-reports/my-reports',
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + citizenToken }
    });

    if (myReportsRes.statusCode !== 200 || !Array.isArray(myReportsRes.data)) {
        console.error('FAIL: My reports fetch failed:', myReportsRes.statusCode, myReportsRes.data);
        failures++;
    } else {
        console.log(`✓ SUCCESS: Retrieved ${myReportsRes.data.length} reports for authenticated citizen.`);
        const found = myReportsRes.data.find(r => r.reportId === createdReportId);
        if (found) {
            console.log(`✓ Verified: Submitted report #${createdReportId} is present in citizen history.`);
            console.log(`  Message preview: ${found.message.substring(0, 60)}...`);
        } else {
            console.error(`FAIL: Submitted report #${createdReportId} not found in my-reports!`);
            failures++;
        }
    }

    // 5. Check Responder Tracking Telemetry Endpoint
    console.log(`\n5. Testing Live Responder Telemetry (/api/tracking/latest/${createdReportId})...`);
    const trackRes = await request({
        hostname: 'localhost',
        port: 8080,
        path: `/api/tracking/latest/${createdReportId}`,
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + citizenToken }
    });

    console.log(`  Tracking response status: ${trackRes.statusCode}`);
    if (trackRes.statusCode === 200) {
        console.log(`✓ SUCCESS: Tracking telemetry response:`, trackRes.data);
    } else {
        console.warn(`  Notice: Tracking status code ${trackRes.statusCode}`);
    }

    // 6. Test Responder / Admin Acknowledgment & Lifecycle Progression
    console.log('\n6. Testing Responder Login and Report Acknowledgment...');
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

    if (respLoginRes.statusCode === 200 && respLoginRes.data.token) {
        const respToken = respLoginRes.data.token;
        console.log('✓ Responder logged in successfully.');

        // Acknowledge report: PUT /api/citizen-reports/{reportId}/acknowledge
        const ackRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: `/api/citizen-reports/${createdReportId}/acknowledge`,
            method: 'PUT',
            headers: { 'Authorization': 'Bearer ' + respToken }
        });

        if (ackRes.statusCode === 200) {
            console.log(`✓ SUCCESS: Incident #${createdReportId} transitioned to 'Acknowledged' (Dispatched).`);

            // Verify citizen view reflects Acknowledged status
            const recheckRes = await request({
                hostname: 'localhost',
                port: 8080,
                path: '/api/citizen-reports/my-reports',
                method: 'GET',
                headers: { 'Authorization': 'Bearer ' + citizenToken }
            });
            const updated = recheckRes.data.find(r => r.reportId === createdReportId);
            if (updated && updated.status === 'Acknowledged') {
                console.log(`✓ SUCCESS: Citizen view correctly reflects updated status 'Acknowledged'! (Stepper stage 2 active)`);
            } else {
                console.error(`FAIL: Updated status not reflected in citizen view:`, updated?.status);
                failures++;
            }

            // Test Resolving the Report: PUT /api/citizen-reports/{reportId}/resolve
            console.log(`\n7. Testing Incident Resolution (/api/citizen-reports/${createdReportId}/resolve)...`);
            const resolveRes = await request({
                hostname: 'localhost',
                port: 8080,
                path: `/api/citizen-reports/${createdReportId}/resolve`,
                method: 'PUT',
                headers: { 'Authorization': 'Bearer ' + respToken }
            });

            if (resolveRes.statusCode === 200) {
                console.log(`✓ SUCCESS: Incident #${createdReportId} transitioned to 'Resolved'.`);

                const recheckRes2 = await request({
                    hostname: 'localhost',
                    port: 8080,
                    path: '/api/citizen-reports/my-reports',
                    method: 'GET',
                    headers: { 'Authorization': 'Bearer ' + citizenToken }
                });
                const resolved = recheckRes2.data.find(r => r.reportId === createdReportId);
                if (resolved && resolved.status === 'Resolved') {
                    console.log(`✓ SUCCESS: Citizen view correctly reflects 'Resolved'! (Stepper completed stage 4)`);
                } else {
                    console.error(`FAIL: Resolved status not reflected:`, resolved?.status);
                    failures++;
                }
            } else {
                console.error(`FAIL: Could not resolve report:`, resolveRes.statusCode, resolveRes.data);
                failures++;
            }
        } else {
            console.error(`FAIL: Could not acknowledge report:`, ackRes.statusCode, ackRes.data);
            failures++;
        }
    } else {
        console.error('FAIL: Responder login failed:', respLoginRes.statusCode, respLoginRes.data);
        failures++;
    }

    console.log('\n===========================================');
    if (failures === 0) {
        console.log('ALL CITIZEN MODULE END-TO-END TESTS PASSED (0 FAILURES)');
    } else {
        console.error(`TESTS FAILED WITH ${failures} FAILURE(S)`);
    }
    console.log('===========================================');
}

run().catch(console.error);
