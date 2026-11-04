package com.disaster.management.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.AdminAuditLog;
import com.disaster.management.entity.User;
import com.disaster.management.repository.AdminAuditLogRepository;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtUtil;

@Service
public class AdminPinService {

    private static final Logger log = LoggerFactory.getLogger(AdminPinService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String generateSecureRecoveryCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("REC-");
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append("-");
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString(); // e.g. REC-7K9M-2P4Q-8W3X
    }

    // Check PIN and recovery status for authenticated admin
    public Map<String, Object> getPinStatus(String adminEmail) {
        Map<String, Object> status = new HashMap<>();
        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        if (adminOpt.isEmpty()) {
            status.put("enrolled", false);
            status.put("locked", false);
            status.put("hasRecoveryCode", false);
            status.put("backupCodeConfigured", false);
            status.put("recoveryConfigured", false);
            return status;
        }

        User admin = adminOpt.get();
        boolean locked = admin.isPinLocked();
        boolean backupLocked = admin.isBackupCodeLocked();
        boolean backupConfigured = admin.getBackupCodeConfigured();

        status.put("enrolled", admin.getAdminPinEnrolled());
        status.put("locked", locked);
        status.put("backupCodeLocked", backupLocked);
        status.put("hasRecoveryCode", admin.getAdminPinRecoveryCode() != null);
        status.put("backupCodeConfigured", backupConfigured);
        status.put("recoveryConfigured", backupConfigured);

        if (locked && admin.getPinLockedUntil() != null) {
            status.put("lockedUntil", admin.getPinLockedUntil().toString());
        }
        if (backupLocked && admin.getBackupCodeLockedUntil() != null) {
            status.put("backupCodeLockedUntil", admin.getBackupCodeLockedUntil().toString());
        }
        return status;
    }

    // Initial one-time emergency backup code setup
    @Transactional
    public Map<String, Object> setupBackupCode(String adminEmail, String backupCode, String confirmBackupCode, String ipAddress) {
        Map<String, Object> result = new HashMap<>();

        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        if (adminOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Administrator account not found");
            return result;
        }

        User admin = adminOpt.get();
        if (!"Admin".equals(admin.getRole())) {
            result.put("success", false);
            result.put("message", "Access denied: User is not an Administrator");
            return result;
        }

        if (backupCode == null || backupCode.trim().length() < 8) {
            result.put("success", false);
            result.put("message", "Backup code must be at least 8 characters long.");
            return result;
        }

        if (confirmBackupCode == null || !backupCode.trim().equals(confirmBackupCode.trim())) {
            result.put("success", false);
            result.put("message", "Backup code and confirmation do not match.");
            return result;
        }

        // Securely hash backup code using BCrypt before storing
        String hashedCode = passwordEncoder.encode(backupCode.trim());
        admin.setAdminPinRecoveryCode(hashedCode);
        admin.setBackupCodeConfigured(true);
        admin.resetBackupCodeFailures();
        userRepository.save(admin);

        recordAudit(adminEmail, "BACKUP_CODE_SETUP", "User", admin.getId().toString(), "SUCCESS", "Emergency backup code configured for 2FA recovery", ipAddress);

        result.put("success", true);
        result.put("message", "Emergency backup code successfully configured. Store it safely offline.");
        return result;
    }

    // Enroll or update PIN (requires current account password verification)
    @Transactional
    public Map<String, Object> enrollPin(String adminEmail, String currentPassword, String newPin, String ipAddress) {
        Map<String, Object> result = new HashMap<>();

        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        if (adminOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Administrator account not found");
            return result;
        }

        User admin = adminOpt.get();
        if (!"Admin".equals(admin.getRole())) {
            result.put("success", false);
            result.put("message", "Access denied: User is not an Administrator");
            return result;
        }

        // Re-authenticate with account password
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, admin.getPassword())) {
            recordAudit(adminEmail, "PIN_ENROLL_FAILED", "User", admin.getId().toString(), "FAILED", "Incorrect password during PIN enrollment", ipAddress);
            result.put("success", false);
            result.put("message", "Current account password verification failed");
            return result;
        }

        // Validate PIN format (exactly 6 digits numeric)
        if (newPin == null || !newPin.matches("^\\d{6}$")) {
            result.put("success", false);
            result.put("message", "PIN must be exactly 6 numeric digits");
            return result;
        }

        // Hash and store PIN
        admin.setAdminPin(passwordEncoder.encode(newPin));
        admin.setAdminPinEnrolled(true);
        admin.resetPinFailures();

        // If recovery backup code is not yet configured, generate default code
        String recoveryCode = null;
        if (!admin.getBackupCodeConfigured() || admin.getAdminPinRecoveryCode() == null) {
            recoveryCode = generateSecureRecoveryCode();
            admin.setAdminPinRecoveryCode(passwordEncoder.encode(recoveryCode));
            admin.setBackupCodeConfigured(true);
        }
        userRepository.save(admin);

        recordAudit(adminEmail, "PIN_ENROLLED", "User", admin.getId().toString(), "SUCCESS", "Administrator PIN successfully enrolled/updated", ipAddress);
        result.put("success", true);
        result.put("message", "Administrator Security PIN configured successfully.");
        if (recoveryCode != null) {
            result.put("recoveryCode", recoveryCode);
        }
        return result;
    }

    // Recover forgotten PIN via verified Emergency Backup Code
    @Transactional
    public Map<String, Object> recoverPinWithBackupCode(
            String adminEmail,
            String backupCode,
            String newPin,
            String confirmPin,
            String newBackupCode,
            String confirmNewBackupCode,
            String ipAddress) {

        Map<String, Object> result = new HashMap<>();

        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        if (adminOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Administrator account not found");
            return result;
        }

        User admin = adminOpt.get();
        if (!"Admin".equals(admin.getRole())) {
            result.put("success", false);
            result.put("message", "Access denied: User is not an Administrator");
            return result;
        }

        // Check if backup code verification is locked due to rate limiting
        if (admin.isBackupCodeLocked()) {
            recordAudit(adminEmail, "BACKUP_CODE_ATTEMPT_LOCKED", "User", admin.getId().toString(), "LOCKED", "Recovery attempted while backup code verification is temporarily locked", ipAddress);
            result.put("success", false);
            result.put("locked", true);
            result.put("message", "Backup code verification is temporarily locked due to consecutive failed attempts. Please retry later.");
            if (admin.getBackupCodeLockedUntil() != null) {
                result.put("lockedUntil", admin.getBackupCodeLockedUntil().toString());
            }
            return result;
        }

        if (admin.getAdminPinRecoveryCode() == null) {
            result.put("success", false);
            result.put("message", "Emergency backup code has not been configured. Please set up recovery first.");
            return result;
        }

        // Verify backup code against stored BCrypt hash
        if (backupCode == null || !passwordEncoder.matches(backupCode.trim(), admin.getAdminPinRecoveryCode())) {
            admin.incrementBackupCodeFailures();
            userRepository.save(admin);

            int remaining = Math.max(0, 5 - admin.getBackupCodeFailedAttempts());
            recordAudit(adminEmail, "PIN_RECOVERY_FAILED", "User", admin.getId().toString(), "FAILED", "Invalid backup code entered during PIN recovery (" + admin.getBackupCodeFailedAttempts() + "/5)", ipAddress);

            result.put("success", false);
            if (admin.isBackupCodeLocked()) {
                result.put("locked", true);
                result.put("message", "Invalid backup code. Recovery verification has been locked for 15 minutes due to 5 consecutive failures.");
                result.put("lockedUntil", admin.getBackupCodeLockedUntil().toString());
            } else {
                result.put("locked", false);
                result.put("remainingAttempts", remaining);
                result.put("message", "Invalid backup code. " + remaining + " attempt(s) remaining before temporary lockout.");
            }
            return result;
        }

        // Backup code verified! Validate new PIN
        if (newPin == null || !newPin.matches("^\\d{6}$")) {
            result.put("success", false);
            result.put("message", "New PIN must be exactly 6 numeric digits.");
            return result;
        }

        if (confirmPin == null || !newPin.equals(confirmPin)) {
            result.put("success", false);
            result.put("message", "New PIN and confirmation PIN do not match.");
            return result;
        }

        // Invalidate old backup code and configure new one if provided
        if (newBackupCode != null && !newBackupCode.trim().isEmpty()) {
            if (newBackupCode.trim().length() < 8) {
                result.put("success", false);
                result.put("message", "New backup code must be at least 8 characters long.");
                return result;
            }
            if (confirmNewBackupCode == null || !newBackupCode.trim().equals(confirmNewBackupCode.trim())) {
                result.put("success", false);
                result.put("message", "New backup code and confirmation do not match.");
                return result;
            }
            admin.setAdminPinRecoveryCode(passwordEncoder.encode(newBackupCode.trim()));
            admin.setBackupCodeConfigured(true);
        } else {
            // Generate a fresh secure recovery code to ensure the old used code is invalidated
            String autoCode = generateSecureRecoveryCode();
            admin.setAdminPinRecoveryCode(passwordEncoder.encode(autoCode));
            admin.setBackupCodeConfigured(true);
            result.put("newRecoveryCode", autoCode);
        }

        // Set new hashed PIN
        admin.setAdminPin(passwordEncoder.encode(newPin));
        admin.setAdminPinEnrolled(true);
        admin.resetPinFailures();
        admin.resetBackupCodeFailures();
        userRepository.save(admin);

        // Generate short-lived step-up token
        String stepUpToken = jwtUtil.generateAdminStepUpToken(adminEmail);

        recordAudit(adminEmail, "PIN_RECOVERED", "User", admin.getId().toString(), "SUCCESS", "Administrator PIN successfully recovered and reset via verified backup code", ipAddress);

        result.put("success", true);
        result.put("message", "Administrator Security PIN reset and recovered successfully.");
        result.put("stepUpToken", stepUpToken);
        result.put("expiresInSeconds", 900);
        return result;
    }

    // Recover and reset forgotten PIN via Password or Emergency Recovery Code
    @Transactional
    public Map<String, Object> recoverPin(String adminEmail, String method, String secret, String newPin, String ipAddress) {
        Map<String, Object> result = new HashMap<>();

        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        if (adminOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Administrator account not found");
            return result;
        }

        User admin = adminOpt.get();
        if (!"Admin".equals(admin.getRole())) {
            result.put("success", false);
            result.put("message", "Access denied: User is not an Administrator");
            return result;
        }

        boolean verified = false;
        if ("RECOVERY_CODE".equalsIgnoreCase(method)) {
            if (admin.getAdminPinRecoveryCode() != null && secret != null) {
                verified = passwordEncoder.matches(secret.trim().toUpperCase(), admin.getAdminPinRecoveryCode())
                        || passwordEncoder.matches(secret.trim(), admin.getAdminPinRecoveryCode());
            }
        } else {
            // Default: account password verification
            if (secret != null) {
                verified = passwordEncoder.matches(secret, admin.getPassword());
            }
        }

        if (!verified) {
            recordAudit(adminEmail, "PIN_RECOVERY_FAILED", "User", admin.getId().toString(), "FAILED", "Failed identity verification during PIN recovery (" + method + ")", ipAddress);
            result.put("success", false);
            result.put("message", "Identity verification failed. Invalid " + ("RECOVERY_CODE".equalsIgnoreCase(method) ? "Emergency Recovery Code" : "Account Password") + ".");
            return result;
        }

        if (newPin == null || !newPin.matches("^\\d{6}$")) {
            result.put("success", false);
            result.put("message", "New PIN must be exactly 6 numeric digits.");
            return result;
        }

        // Set new hashed PIN
        admin.setAdminPin(passwordEncoder.encode(newPin));
        admin.setAdminPinEnrolled(true);
        admin.resetPinFailures();
        admin.resetBackupCodeFailures();

        // Invalidate old recovery code and issue a fresh one
        String newRecoveryCode = generateSecureRecoveryCode();
        admin.setAdminPinRecoveryCode(passwordEncoder.encode(newRecoveryCode));
        admin.setBackupCodeConfigured(true);
        userRepository.save(admin);

        recordAudit(adminEmail, "PIN_RECOVERED", "User", admin.getId().toString(), "SUCCESS", "Administrator PIN successfully recovered and reset via " + method, ipAddress);

        result.put("success", true);
        result.put("message", "Administrator Security PIN reset and recovered successfully.");
        result.put("recoveryCode", newRecoveryCode);
        return result;
    }

    // Verify PIN with strict rate-limiting and temporary lockout
    @Transactional
    public Map<String, Object> verifyPin(String adminEmail, String pin, String ipAddress) {
        Map<String, Object> result = new HashMap<>();

        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        if (adminOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Invalid authentication state");
            return result;
        }

        User admin = adminOpt.get();
        if (!"Admin".equals(admin.getRole())) {
            result.put("success", false);
            result.put("message", "Access denied");
            return result;
        }

        // Check if currently locked out
        if (admin.isPinLocked()) {
            recordAudit(adminEmail, "PIN_ATTEMPT_LOCKED", "User", admin.getId().toString(), "LOCKED", "Attempt made while account PIN is temporarily locked", ipAddress);
            result.put("success", false);
            result.put("locked", true);
            result.put("message", "Security PIN is locked due to consecutive failed attempts. Please retry later.");
            result.put("lockedUntil", admin.getPinLockedUntil().toString());
            return result;
        }

        // Check if PIN has been enrolled
        if (!admin.getAdminPinEnrolled() || admin.getAdminPin() == null) {
            result.put("success", false);
            result.put("enrolled", false);
            result.put("message", "Administrator PIN has not been enrolled yet. Please enroll first.");
            return result;
        }

        // Verify PIN against BCrypt hash
        if (pin != null && passwordEncoder.matches(pin, admin.getAdminPin())) {
            // Success: Reset failure count
            admin.resetPinFailures();
            userRepository.save(admin);

            // Generate short-lived step-up token
            String stepUpToken = jwtUtil.generateAdminStepUpToken(adminEmail);

            recordAudit(adminEmail, "PIN_VERIFIED", "User", admin.getId().toString(), "SUCCESS", "Admin step-up 2FA verified successfully", ipAddress);

            result.put("success", true);
            result.put("message", "Security PIN verified successfully");
            result.put("stepUpToken", stepUpToken);
            result.put("expiresInSeconds", 900); // 15 minutes
            return result;
        } else {
            // Failure: Increment failed attempts and trigger lockout if limit reached
            admin.incrementPinFailures();
            userRepository.save(admin);

            int remaining = Math.max(0, 3 - admin.getPinFailedAttempts());
            recordAudit(adminEmail, "PIN_VERIFY_FAILED", "User", admin.getId().toString(), "FAILED", "Failed PIN verification attempt (" + admin.getPinFailedAttempts() + "/3)", ipAddress);

            result.put("success", false);
            if (admin.isPinLocked()) {
                result.put("locked", true);
                result.put("message", "Incorrect PIN. Account PIN verification has been locked for 15 minutes due to 3 consecutive failures.");
                result.put("lockedUntil", admin.getPinLockedUntil().toString());
            } else {
                result.put("locked", false);
                result.put("remainingAttempts", remaining);
                result.put("message", "Invalid Security PIN. " + remaining + " attempt(s) remaining before temporary lockout.");
            }
            return result;
        }
    }

    // Verify whether an incoming step-up token is valid for privileged operations
    public boolean validateStepUpAuthorization(String stepUpToken, String expectedAdminEmail) {
        if (stepUpToken == null || stepUpToken.trim().isEmpty()) {
            return false;
        }
        if (stepUpToken.startsWith("Bearer ")) {
            stepUpToken = stepUpToken.substring(7).trim();
        }

        if (!jwtUtil.isStepUpToken(stepUpToken)) {
            return false;
        }

        String email = jwtUtil.extractEmail(stepUpToken);
        return email != null && email.equalsIgnoreCase(expectedAdminEmail);
    }

    // Record administrative audit trail entry
    public void recordAudit(String actorEmail, String action, String targetEntity, String targetId, String status, String details, String ipAddress) {
        try {
            AdminAuditLog logEntry = new AdminAuditLog(actorEmail, action, targetEntity, targetId, status, details, ipAddress);
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to record admin audit log: {}", e.getMessage());
        }
    }

    // Retrieve recent administrative audit trail entries
    public List<AdminAuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }
}
