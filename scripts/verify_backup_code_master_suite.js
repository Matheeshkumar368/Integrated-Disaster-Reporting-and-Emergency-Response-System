/**
 * Master Verification Suite:
 * 1. Admin Login & Role Authentication
 * 2. Status of Security PIN and Emergency Backup Code
 * 3. Recovery Rejection with Incorrect Backup Code & Attempt Counter
 * 4. Recovery Acceptance with Intended Emergency Backup Code (922523244035)
 * 5. PIN Reset & Verification (Old PIN fails, New PIN works)
 * 6. Backup Code Retention when Replacement is Blank
 * 7. Backup Code Replacement & Invalidation
 * 8. 2FA Audit Trail Verification
 * 9. Regression Testing: Citizen & Responder Authentication
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

async function runMasterVerification() {
    console.log('========================================================================');
    console.log('MASTER VERIFICATION: ADMIN BACKUP CODE RECOVERY & 2FA SECURITY');
    console.log('Intended Emergency Backup Code: 922523244035');
    console.log('========================================================================\n');

    let passed = 0;
    let total = 0;

    function assertCheck(name, condition, extra = '') {
        total++;
        if (condition) {
            passed++;
            console.log(`[PASS] Check ${total}: ${name}`);
            if (extra) console.log(`       -> ${extra}`);
        } else {
            console.error(`[FAIL] Check ${total}: ${name}`);
            if (extra) console.error(`       -> ${extra}`);
        }
    }

    try {
        // --- TEST 1: Role Authentication (Citizen & Responder) ---
        console.log('--- 1. Civilian & Responder Role Regression Verification ---');
        const citizenRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/login`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'citizen@gmail.com', password: 'citizen123' });

        assertCheck(
            'Citizen login succeeds (HTTP 200, role = Citizen)',
            citizenRes.status === 200 && citizenRes.body.role === 'Citizen' && !!citizenRes.body.token,
            `User: ${citizenRes.body.name}, Role: ${citizenRes.body.role}`
        );

        const responderRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/login`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'responder@disaster.gov.in', password: 'responder123' });

        assertCheck(
            'Responder login succeeds (HTTP 200, role = Responder)',
            responderRes.status === 200 && responderRes.body.role === 'Responder' && !!responderRes.body.token,
            `User: ${responderRes.body.name}, Role: ${responderRes.body.role}`
        );

        // --- TEST 2: Admin Authentication ---
        console.log('\n--- 2. Administrator Authentication & 2FA Status ---');
        const adminRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/login`),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'admin@disaster.gov.in', password: 'admin123' });

        assertCheck(
            'Admin login succeeds (HTTP 200, role = Admin)',
            adminRes.status === 200 && adminRes.body.role === 'Admin' && !!adminRes.body.token,
            `Token length: ${adminRes.body.token.length}`
        );
        const adminToken = adminRes.body.token;

        // Ensure 922523244035 is seeded via /setup if needed
        const setupRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/backup-code/setup`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, { backupCode: '922523244035', confirmBackupCode: '922523244035' });

        assertCheck(
            'Admin backup code setup endpoint accepts 922523244035',
            setupRes.status === 200 && setupRes.body.success === true,
            `Setup message: ${setupRes.body.message}`
        );

        const statusRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/status`),
            method: 'GET',
            headers: { 'Authorization': `Bearer ${adminToken}` }
        });

        assertCheck(
            'Security PIN status reflects enrolled=true and backupCodeConfigured=true',
            statusRes.status === 200 && statusRes.body.enrolled === true && statusRes.body.backupCodeConfigured === true,
            `enrolled: ${statusRes.body.enrolled}, backupCodeConfigured: ${statusRes.body.backupCodeConfigured}`
        );

        // --- TEST 3: Recovery with Incorrect Backup Code ---
        console.log('\n--- 3. Recovery Verification with Invalid Backup Code ---');
        const invalidRecovery = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: 'INVALID_CODE_9999',
            newPin: '778899',
            confirmPin: '778899'
        });

        assertCheck(
            'Invalid backup code is rejected with HTTP 400 and failure message',
            invalidRecovery.status === 400 && invalidRecovery.body.success === false,
            `Message: ${invalidRecovery.body.message}`
        );

        // --- TEST 4: Recovery with Correct Backup Code (922523244035) ---
        console.log('\n--- 4. Recovery with Intended Backup Code (922523244035) & PIN Reset ---');
        const validRecovery = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: '922523244035',
            newPin: '654321',
            confirmPin: '654321'
            // newBackupCode is left blank: tests retention of 922523244035!
        });

        assertCheck(
            'Recovery with 922523244035 succeeds, resets PIN to 654321, and returns stepUpToken',
            validRecovery.status === 200 && validRecovery.body.success === true && !!validRecovery.body.stepUpToken,
            `Message: ${validRecovery.body.message}, stepUpToken issued: ${!!validRecovery.body.stepUpToken}`
        );
        let currentStepUpToken = validRecovery.body.stepUpToken;

        // --- TEST 5: Verify Old PIN vs New PIN ---
        console.log('\n--- 5. Step-Up Verification of New PIN (654321) & Invalidation of Old PIN (123456) ---');
        const oldPinVerify = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/verify-pin`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, { pin: '123456' });

        assertCheck(
            'Old PIN (123456) is rejected after reset',
            (oldPinVerify.status === 401 || oldPinVerify.status === 400) && oldPinVerify.body.success === false,
            `Status: ${oldPinVerify.status}, Message: ${oldPinVerify.body.message}`
        );

        const newPinVerify = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/verify-pin`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, { pin: '654321' });

        assertCheck(
            'New PIN (654321) is verified successfully',
            newPinVerify.status === 200 && newPinVerify.body.success === true && !!newPinVerify.body.stepUpToken,
            `Message: ${newPinVerify.body.message}`
        );

        // --- TEST 6: Verify Backup Code Retention ---
        console.log('\n--- 6. Verify Backup Code Retention (922523244035 remained valid after reset) ---');
        const secondRecovery = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: '922523244035',
            newPin: '123456',
            confirmPin: '123456'
        });

        assertCheck(
            'Backup code 922523244035 was retained and succeeds again to reset PIN back to 123456',
            secondRecovery.status === 200 && secondRecovery.body.success === true,
            `Message: ${secondRecovery.body.message}`
        );

        // Confirm 123456 works again
        const restoredPinVerify = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/verify-pin`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, { pin: '123456' });

        assertCheck(
            'Restored Security PIN (123456) verifies successfully',
            restoredPinVerify.status === 200 && restoredPinVerify.body.success === true,
            `Message: ${restoredPinVerify.body.message}`
        );

        // --- TEST 7: Replacement Backup Code Functionality ---
        console.log('\n--- 7. Replacement Backup Code Rotation & Old Code Invalidation ---');
        const replacementCode = 'NewEmergencyCode2026!';
        const rotateRecovery = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: '922523244035',
            newPin: '123456',
            confirmPin: '123456',
            newBackupCode: replacementCode,
            confirmNewBackupCode: replacementCode
        });

        assertCheck(
            'PIN recovery with explicit replacement backup code succeeds and rotates code',
            rotateRecovery.status === 200 && rotateRecovery.body.success === true && rotateRecovery.body.backupCodeReplaced === true,
            `Message: ${rotateRecovery.body.message}, Replaced: ${rotateRecovery.body.backupCodeReplaced}`
        );

        // Verify that 922523244035 is now invalidated
        const invalidatedCodeCheck = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: '922523244035', // should now fail
            newPin: '123456',
            confirmPin: '123456'
        });

        assertCheck(
            'Prior backup code (922523244035) is invalidated after rotation',
            invalidatedCodeCheck.status === 400 && invalidatedCodeCheck.body.success === false,
            `Message: ${invalidatedCodeCheck.body.message}`
        );

        // Verify that replacement code works
        const replacementVerify = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: replacementCode,
            newPin: '123456',
            confirmPin: '123456',
            newBackupCode: '922523244035', // Rotate back to 922523244035
            confirmNewBackupCode: '922523244035'
        });

        assertCheck(
            'Replacement code works and successfully restores intended code (922523244035)',
            replacementVerify.status === 200 && replacementVerify.body.success === true,
            `Message: ${replacementVerify.body.message}`
        );

        // Verify 922523244035 is once again active and verified
        const finalCheck = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/security-pin/recover-with-backup-code`),
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'Content-Type': 'application/json'
            }
        }, {
            backupCode: '922523244035',
            newPin: '123456',
            confirmPin: '123456'
        });

        assertCheck(
            'Final state: Intended emergency backup code (922523244035) is active and functional',
            finalCheck.status === 200 && finalCheck.body.success === true,
            `Message: ${finalCheck.body.message}`
        );
        currentStepUpToken = finalCheck.body.stepUpToken;

        // --- TEST 8: 2FA Audit Trail ---
        console.log('\n--- 8. Administrative 2FA Audit Trail Verification ---');
        const auditRes = await request({
            ...parseUrl(`${BASE_URL}/api/auth/admin/audit-logs`),
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${adminToken}`,
                'X-Admin-Step-Up-Token': currentStepUpToken
            }
        });

        const logs = Array.isArray(auditRes.body) ? auditRes.body : [];
        const hasRecoveredAudit = logs.some(l => l.action === 'PIN_RECOVERED');
        const hasFailedAudit = logs.some(l => l.action === 'PIN_RECOVERY_FAILED');

        assertCheck(
            'Admin Audit Trail records PIN_RECOVERED and PIN_RECOVERY_FAILED events',
            auditRes.status === 200 && hasRecoveredAudit && hasFailedAudit,
            `Total audit records: ${logs.length}, PIN_RECOVERED present: ${hasRecoveredAudit}, PIN_RECOVERY_FAILED present: ${hasFailedAudit}`
        );

        console.log('\n========================================================================');
        console.log(`MASTER VERIFICATION RESULTS: ${passed}/${total} checks PASSED`);
        console.log('========================================================================\n');

        if (passed === total) {
            process.exit(0);
        } else {
            process.exit(1);
        }

    } catch (err) {
        console.error('Fatal error during verification:', err);
        process.exit(1);
    }
}

runMasterVerification();
