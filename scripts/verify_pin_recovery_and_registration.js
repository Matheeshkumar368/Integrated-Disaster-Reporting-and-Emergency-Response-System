/**
 * Comprehensive Automated Verification Suite for:
 * 1. Admin 2FA PIN Recovery & Emergency Backup Code
 * 2. New User Registration (Citizen, Responder, Admin self-reg prevention)
 * 3. Validation & Duplicate Handling
 * 4. Audit Trail Verification
 */

const http = require('http');

const BASE_URL = 'http://localhost:8080';

function request(options, body = null) {
    return new Promise((resolve, reject) => {
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
                resolve({ status: res.statusCode, headers: res.headers, body: parsed });
            });
        });
        req.on('error', reject);
        if (body) {
            req.write(typeof body === 'string' ? body : JSON.stringify(body));
        }
        req.end();
    });
}

function parseUrl(urlStr) {
    const u = new URL(urlStr);
    return {
        hostname: u.hostname,
        port: u.port || 80,
        path: u.pathname + u.search
    };
}

async function runTests() {
    console.log('========================================================================');
    console.log('ADMIN PIN RECOVERY, BACKUP CODE & REGISTRATION VERIFICATION SUITE');
    console.log('========================================================================\n');

    let passed = 0;
    let total = 0;

    function assertCheck(name, condition, extra = '') {
        total++;
        if (condition) {
            passed++;
            console.log(`[PASS] Check ${total}: ${name}`);
            if (extra) console.log(`       ${extra}`);
        } else {
            console.error(`[FAIL] Check ${total}: ${name}`);
            if (extra) console.error(`       ${extra}`);
        }
    }

    try {
        // --- 1. Citizen Registration Tests ---
        console.log('--- 1. Civilian User Registration & Validation ---');

        const uniqueCitizenEmail = `citizen_${Date.now()}@example.com`;
        const citizenPayload = {
            name: 'Kavitha Devi',
            email: uniqueCitizenEmail,
            password: 'CitizenPassword@123',
            phone: '+91 9876543210',
            location: 'Chennai',
            role: 'Citizen'
        };

        const regRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/register`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, citizenPayload);

        assertCheck(
            'Valid Citizen registration returns HTTP 200 and success status',
            regRes.status === 200 && regRes.body.status === 'success',
            `Status: ${regRes.status}, Message: ${regRes.body.message}`
        );

        // Duplicate email rejection
        const dupRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/register`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, citizenPayload);

        assertCheck(
            'Duplicate email registration is rejected with HTTP 409 Conflict',
            dupRes.status === 409 && dupRes.body.message.includes('Email already registered'),
            `Status: ${dupRes.status}, Message: ${dupRes.body.message}`
        );

        // Validation rejection: invalid email
        const badEmailRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/register`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { ...citizenPayload, email: 'notanemail' });

        assertCheck(
            'Invalid email format is rejected with HTTP 400 Bad Request',
            badEmailRes.status === 400,
            `Status: ${badEmailRes.status}`
        );

        // Validation rejection: password < 6 chars
        const shortPassRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/register`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { ...citizenPayload, email: `valid_${Date.now()}@example.com`, password: '123' });

        assertCheck(
            'Password shorter than 6 characters is rejected with HTTP 400',
            shortPassRes.status === 400,
            `Status: ${shortPassRes.status}`
        );

        // Validation rejection: invalid phone format
        const badPhoneRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/register`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { ...citizenPayload, email: `valid_p_${Date.now()}@example.com`, phone: 'bad-phone-abc' });

        assertCheck(
            'Invalid phone number format is rejected with HTTP 400',
            badPhoneRes.status === 400,
            `Status: ${badPhoneRes.status}`
        );

        // Prevent Admin self-registration
        const adminSelfReg = await request({
            ...parseUrl(`${BASE_URL}/api/auth/register`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { ...citizenPayload, email: `hacker_admin_${Date.now()}@example.com`, role: 'Admin' });

        assertCheck(
            'Administrative account self-registration is rejected with HTTP 403 Forbidden',
            adminSelfReg.status === 403,
            `Status: ${adminSelfReg.status}, Message: ${adminSelfReg.body.message}`
        );

        // Login with newly registered civilian credentials
        const loginRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/login`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: uniqueCitizenEmail, password: 'CitizenPassword@123' });

        assertCheck(
            'Newly registered Citizen can authenticate and obtain JWT token',
            loginRes.status === 200 && loginRes.body.token != null && loginRes.body.role === 'Citizen',
            `Status: ${loginRes.status}, Role: ${loginRes.body.role}`
        );

        // --- 2. Responder Registration Tests ---
        console.log('\n--- 2. Responder Registration & Accreditation ---');

        const uniqueResponderEmail = `responder_${Date.now()}@disaster.gov.in`;
        const responderPayload = {
            name: 'Officer Rajesh Kumar',
            email: uniqueResponderEmail,
            password: 'ResponderSecurePassword@123',
            phone: '+91 9444012345',
            location: 'Coimbatore',
            assignedZone: 'Coimbatore North',
            groupClassification: 'Leader',
            additionalDetails: 'NDRF Certified Disaster Specialist'
        };

        const respRegRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/register-responder`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, responderPayload);

        assertCheck(
            'Valid Responder registration returns HTTP 200 and success status',
            respRegRes.status === 200 && respRegRes.body.status === 'success',
            `Status: ${respRegRes.status}, Message: ${respRegRes.body.message}`
        );

        // Login with newly registered responder credentials
        const respLoginRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/login`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: uniqueResponderEmail, password: 'ResponderSecurePassword@123' });

        assertCheck(
            'Newly registered Responder can authenticate and obtain JWT token with role Responder',
            respLoginRes.status === 200 && respLoginRes.body.token != null && respLoginRes.body.role === 'Responder',
            `Status: ${respLoginRes.status}, Role: ${respLoginRes.body.role}`
        );

        // --- 3. Admin PIN & Backup Code Recovery Tests ---
        console.log('\n--- 3. Admin 2FA PIN & Backup Code Recovery ---');

        // Authenticate admin
        const adminLogin = await request({
            ...parseUrl(`${BASE_URL}/api/auth/login`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'admin@disaster.gov.in', password: 'admin123' });

        assertCheck(
            'Admin authentication succeeds and returns Admin JWT',
            adminLogin.status === 200 && adminLogin.body.token != null,
            `Role: ${adminLogin.body.role}`
        );
        const adminToken = adminLogin.body.token;

        // Check PIN & Recovery Status
        const statusRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/status`),
            method: 'GET',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });

        assertCheck(
            'Admin PIN status endpoint returns backupCodeConfigured field',
            statusRes.status === 200 && ('backupCodeConfigured' in statusRes.body),
            `backupCodeConfigured: ${statusRes.body.backupCodeConfigured}`
        );

        // Initial Backup Code Setup
        const backupSetupCode = 'MasterEmergencyBackupCode2026!';
        const setupRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/backup-code/setup`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, { backupCode: backupSetupCode, confirmBackupCode: backupSetupCode });

        assertCheck(
            'Admin initial emergency backup code setup returns HTTP 200 and success',
            setupRes.status === 200 && setupRes.body.success === true,
            `Message: ${setupRes.body.message}`
        );

        // Check status again - backupCodeConfigured must now be TRUE
        const statusAfterSetup = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/status`),
            method: 'GET',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });

        assertCheck(
            'PIN status reflects backupCodeConfigured = true (prevents repeated prompts)',
            statusAfterSetup.status === 200 && statusAfterSetup.body.backupCodeConfigured === true,
            `backupCodeConfigured: ${statusAfterSetup.body.backupCodeConfigured}`
        );

        // Test Recovery with INVALID Backup Code
        const badRecoverRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: 'WrongIncorrectBackupCode',
            newPin: '998877',
            confirmPin: '998877'
        });

        assertCheck(
            'PIN recovery with invalid backup code is rejected with HTTP 400',
            badRecoverRes.status === 400 && badRecoverRes.body.success === false,
            `Message: ${badRecoverRes.body.message}`
        );

        // Test Recovery with VALID Backup Code & Set New 6-Digit PIN
        const newBackupCode = 'SecondaryRecoveryCode2026Secure!';
        const recoverRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: backupSetupCode,
            newPin: '654321',
            confirmPin: '654321',
            newBackupCode: newBackupCode,
            confirmNewBackupCode: newBackupCode
        });

        assertCheck(
            'PIN recovery with valid backup code succeeds and returns stepUpToken',
            recoverRes.status === 200 && recoverRes.body.success === true && recoverRes.body.stepUpToken != null,
            `Message: ${recoverRes.body.message}`
        );
        const stepUpToken = recoverRes.body.stepUpToken;

        // Verify Step-Up Authentication with the NEW 6-digit PIN
        const verifyNewPin = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/verify-pin`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, { pin: '654321' });

        assertCheck(
            'New Security PIN (654321) verifies successfully against BCrypt hash',
            verifyNewPin.status === 200 && verifyNewPin.body.success === true,
            `Message: ${verifyNewPin.body.message}`
        );

        // Verify Old Backup Code was Invalidated
        const oldCodeReuse = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: backupSetupCode, // Old used code
            newPin: '112233',
            confirmPin: '112233'
        });

        assertCheck(
            'Old backup code is invalidated and cannot be reused',
            oldCodeReuse.status === 400 && oldCodeReuse.body.success === false,
            `Message: ${oldCodeReuse.body.message}`
        );

        // --- 4. Audit Trail Verification ---
        console.log('\n--- 4. Administrative 2FA Audit Trail ---');

        const auditRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/audit-logs`),
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'X-Admin-Step-Up-Token': stepUpToken
            }
        });

        assertCheck(
            'Audit logs retrieved successfully with stepUpToken authorization',
            auditRes.status === 200 && Array.isArray(auditRes.body) && auditRes.body.length > 0,
            `Log count: ${Array.isArray(auditRes.body) ? auditRes.body.length : 0}`
        );

        const actions = Array.isArray(auditRes.body) ? auditRes.body.map(l => l.action) : [];
        assertCheck(
            'Audit trail contains BACKUP_CODE_SETUP and PIN_RECOVERED operational entries',
            actions.includes('BACKUP_CODE_SETUP') && actions.includes('PIN_RECOVERED'),
            `Found actions: ${actions.filter(a => a.includes('PIN') || a.includes('BACKUP')).join(', ')}`
        );

        // --- 5. System Health Check ---
        console.log('\n--- 5. Platform Integrity Check ---');

        const health = await request({
            ...parseUrl(`${BASE_URL}/actuator/health`),
            method: 'GET'
        });

        assertCheck(
            'Actuator health reports status UP with MySQL connected',
            health.status === 200 && health.body.status === 'UP' && health.body.components.db.status === 'UP',
            `Database: ${health.body.components.db.details.database}`
        );

        console.log('\n========================================================================');
        console.log(`VERIFICATION RESULT: ${passed} OF ${total} CHECKS PASSED (${Math.round(passed / total * 100)}%)`);
        console.log('========================================================================');

        if (passed === total) {
            process.exit(0);
        } else {
            process.exit(1);
        }

    } catch (err) {
        console.error('Test execution exception:', err);
        process.exit(1);
    }
}

runTests();
