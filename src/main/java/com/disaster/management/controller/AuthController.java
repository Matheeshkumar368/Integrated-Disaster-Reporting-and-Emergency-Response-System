package com.disaster.management.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.AdminAuditLog;
import com.disaster.management.entity.User;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtUtil;
import com.disaster.management.dto.BackupCodeSetupDto;
import com.disaster.management.dto.PinRecoveryDto;
import com.disaster.management.dto.UserRegistrationDto;
import com.disaster.management.service.AdminPinService;
import com.disaster.management.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081"})
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RescueTaskRepository rescueTaskRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminPinService adminPinService;

    @Autowired
    private UserService userService;

    // In-memory login attempt tracker for rate-limiting
    private static class LoginAttempt {
        int attempts;
        LocalDateTime lockedUntil;
    }
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    // ────────────────────────────────────────────────
    //                  REGISTER ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody UserRegistrationDto userDto) {
        Map<String, String> response = new HashMap<>();
        try {
            User user = userService.registerUser(userDto);
            response.put("status", "success");
            response.put("message", "User Registered Successfully!");
            response.put("role", user.getRole());
            return ResponseEntity.ok(response);
        } catch (SecurityException se) {
            response.put("status", "error");
            response.put("message", se.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        } catch (IllegalStateException ise) {
            response.put("status", "error");
            response.put("message", ise.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (IllegalArgumentException iae) {
            response.put("status", "error");
            response.put("message", iae.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Registration failed due to a server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    // ────────────────────────────────────────────────
    //                     LOGIN ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String email = request.get("email");
        String password = request.get("password");

        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Email and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        String normalizedEmail = email.trim().toLowerCase();

        // Check rate-limiting / lockout
        LoginAttempt attempt = loginAttempts.get(normalizedEmail);
        if (attempt != null && attempt.lockedUntil != null && attempt.lockedUntil.isAfter(LocalDateTime.now())) {
            response.put("status", "error");
            response.put("message", "Account temporarily locked due to consecutive failed logins. Please retry in a few minutes.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (passwordEncoder.matches(password, user.getPassword())) {
                // Reset failed attempts on success
                loginAttempts.remove(normalizedEmail);
                user.setLastLoginAt(LocalDateTime.now());
                userRepository.save(user);

                String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getName());
                response.put("status", "success");
                response.put("message", "Login successful");
                response.put("token", token);
                response.put("role", user.getRole());
                response.put("name", user.getName() != null ? user.getName() : user.getEmail().split("@")[0]);
                response.put("adminPinEnrolled", user.getAdminPinEnrolled());
                response.put("sosShortcutEnabled", user.getSosShortcutEnabled());
                response.put("sosShortcutKey", user.getSosShortcutKey());
                response.put("sosSoundVolume", user.getSosSoundVolume());

                log.info("Successful login for user role: {}", user.getRole());
                return ResponseEntity.ok(response);
            }
        }

        // Generic failure response (preventing user enumeration)
        LoginAttempt failAttempt = loginAttempts.computeIfAbsent(normalizedEmail, k -> new LoginAttempt());
        failAttempt.attempts++;
        if (failAttempt.attempts >= 5) {
            failAttempt.lockedUntil = LocalDateTime.now().plusMinutes(5);
            log.warn("Login threshold exceeded for account; locking for 5 minutes.");
        }

        response.put("status", "error");
        response.put("message", "Invalid email or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // ────────────────────────────────────────────────
    //              GET USER PROFILE ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();

        String token = extractBearerToken(authHeader);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Authorization token required or expired");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = jwtUtil.extractEmail(token);
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            response.put("status", "success");
            response.put("name", user.getName());
            response.put("email", user.getEmail());
            response.put("phone", user.getPhone());
            response.put("location", user.getLocation());
            response.put("role", user.getRole());
            response.put("assignedZone", user.getAssignedZone());
            response.put("adminPinEnrolled", user.getAdminPinEnrolled());
            response.put("sosShortcutEnabled", user.getSosShortcutEnabled());
            response.put("sosShortcutKey", user.getSosShortcutKey());
            response.put("sosSoundVolume", user.getSosSoundVolume());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "User account not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // ────────────────────────────────────────────────
    //              UPDATE USER PROFILE ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/update-profile")
    public ResponseEntity<Map<String, String>> updateProfile(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Map<String, String> response = new HashMap<>();

        String token = extractBearerToken(authHeader);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Authorization token required or expired");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String authenticatedEmail = jwtUtil.extractEmail(token);
        Optional<User> authenticatedUserOptional = userRepository.findByEmail(authenticatedEmail);
        if (authenticatedUserOptional.isEmpty()) {
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = authenticatedUserOptional.get();

        if (request.containsKey("name") && request.get("name") != null && !request.get("name").trim().isEmpty()) {
            user.setName(request.get("name").trim());
        }
        if (request.containsKey("phone") && request.get("phone") != null) {
            user.setPhone(request.get("phone").trim());
        }
        if (request.containsKey("location") && request.get("location") != null) {
            user.setLocation(request.get("location").trim());
        }
        if (request.containsKey("sosShortcutEnabled") && request.get("sosShortcutEnabled") != null) {
            user.setSosShortcutEnabled(Boolean.parseBoolean(String.valueOf(request.get("sosShortcutEnabled"))));
        }
        if (request.containsKey("sosShortcutKey") && request.get("sosShortcutKey") != null && !request.get("sosShortcutKey").trim().isEmpty()) {
            user.setSosShortcutKey(request.get("sosShortcutKey").trim());
        }
        if (request.containsKey("sosSoundVolume") && request.get("sosSoundVolume") != null) {
            try {
                int vol = Integer.parseInt(String.valueOf(request.get("sosSoundVolume")));
                user.setSosSoundVolume(Math.max(0, Math.min(100, vol)));
            } catch (NumberFormatException ignored) {}
        }
        if (request.containsKey("password") && request.get("password") != null && !request.get("password").trim().isEmpty()) {
            if (request.get("password").length() >= 6) {
                user.setPassword(passwordEncoder.encode(request.get("password")));
            } else {
                response.put("status", "error");
                response.put("message", "New password must be at least 6 characters");
                return ResponseEntity.badRequest().body(response);
            }
        }

        try {
            userRepository.save(user);
            response.put("status", "success");
            response.put("message", "Profile updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to update profile");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ────────────────────────────────────────────────
    //              REGISTER RESPONDER (Public)
    // ────────────────────────────────────────────────
    @PostMapping("/register-responder")
    public ResponseEntity<Map<String, Object>> registerResponder(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        UserRegistrationDto dto = new UserRegistrationDto();
        dto.setName(request.get("name"));
        dto.setEmail(request.get("email"));
        dto.setPassword(request.get("password"));
        dto.setPhone(request.get("phone"));
        dto.setLocation(request.get("location"));
        dto.setAssignedZone(request.get("assignedZone") != null ? request.get("assignedZone") : request.get("location"));
        dto.setRole("Responder");
        dto.setGroupClassification(request.get("groupClassification") != null ? request.get("groupClassification") : "Rescuer");
        dto.setAdditionalDetails(request.get("additionalDetails"));

        try {
            User user = userService.registerUser(dto);
            response.put("status", "success");
            response.put("message", "Responder registered successfully!");
            response.put("role", user.getRole());
            return ResponseEntity.ok(response);
        } catch (SecurityException se) {
            response.put("status", "error");
            response.put("message", se.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        } catch (IllegalStateException ise) {
            response.put("status", "error");
            response.put("message", ise.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (IllegalArgumentException iae) {
            response.put("status", "error");
            response.put("message", iae.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Responder registration error: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Registration failed due to a server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    // ────────────────────────────────────────────────
    //          ADMIN: RESPONDERS BY ZONE
    // ────────────────────────────────────────────────
    @GetMapping("/responders/by-zone")
    public ResponseEntity<Map<String, Object>> getRespondersByZone() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<User> responders = userRepository.findByRole("Responder");
            Map<String, Integer> zoneCounts = new LinkedHashMap<>();

            for (User responder : responders) {
                String zone = responder.getAssignedZone();
                if (zone == null || zone.isEmpty()) {
                    zone = "Unassigned";
                }
                zoneCounts.put(zone, zoneCounts.getOrDefault(zone, 0) + 1);
            }

            response.put("status", "success");
            response.put("zoneCounts", zoneCounts);
            response.put("totalResponders", responders.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to retrieve responder metrics");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ────────────────────────────────────────────────
    //          ADMIN: GET ALL RESPONDERS
    // ────────────────────────────────────────────────
    @GetMapping("/responders/all")
    public ResponseEntity<Map<String, Object>> getAllResponders() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<User> responders = userRepository.findByRole("Responder");
            List<Map<String, Object>> responderList = new ArrayList<>();

            for (User responder : responders) {
                Map<String, Object> r = new HashMap<>();
                r.put("id", responder.getId());
                r.put("name", responder.getName());
                r.put("email", responder.getEmail());
                r.put("phone", responder.getPhone());
                r.put("location", responder.getLocation());
                r.put("assignedZone", responder.getAssignedZone());
                r.put("groupClassification", responder.getGroupClassification());
                r.put("additionalDetails", responder.getAdditionalDetails());
                responderList.add(r);
            }

            response.put("status", "success");
            response.put("responders", responderList);
            response.put("totalCount", responderList.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to retrieve responders");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ────────────────────────────────────────────────
    //          ADMIN: ASSIGN ZONE TO RESPONDER
    // ────────────────────────────────────────────────
    @PostMapping("/assign-zone")
    public ResponseEntity<Map<String, String>> assignZone(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-Admin-Step-Up-Token", required = false) String stepUpToken,
            HttpServletRequest httpRequest) {

        Map<String, String> response = new HashMap<>();

        String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (adminEmail == null || adminEmail.equals("anonymousUser")) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // Verify Step-Up Admin Security PIN authorization
        if (!adminPinService.validateStepUpAuthorization(stepUpToken, adminEmail)) {
            response.put("status", "step_up_required");
            response.put("message", "Admin Security PIN step-up verification required for assigning responder zones");
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(response);
        }

        String responderEmail = request.get("responderEmail");
        String zone = request.get("zone");

        if (responderEmail == null || responderEmail.trim().isEmpty() || zone == null || zone.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Responder email and target zone are required");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> responderOpt = userRepository.findByEmail(responderEmail.trim().toLowerCase());
        if (responderOpt.isEmpty() || !"Responder".equals(responderOpt.get().getRole())) {
            response.put("status", "error");
            response.put("message", "Valid responder account not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User responder = responderOpt.get();
        responder.setAssignedZone(zone.trim());
        userRepository.save(responder);

        adminPinService.recordAudit(adminEmail, "ASSIGN_ZONE", "User", responder.getId().toString(), "SUCCESS", "Assigned responder to zone: " + zone, httpRequest.getRemoteAddr());

        response.put("status", "success");
        response.put("message", "Zone assigned successfully to responder");
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────
    //        ADMIN SECURITY PIN: ENROLL / SETUP
    // ────────────────────────────────────────────────
    @PostMapping({"/admin/pin/enroll", "/admin/security-pin/configure"})
    public ResponseEntity<Map<String, Object>> enrollAdminPin(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        String currentPassword = request.get("currentPassword");
        String pin = request.get("pin");

        Map<String, Object> result = adminPinService.enrollPin(adminEmail, currentPassword, pin, httpRequest.getRemoteAddr());
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ────────────────────────────────────────────────
    //        ADMIN BACKUP CODE: ONE-TIME SETUP
    // ────────────────────────────────────────────────
    @PostMapping({"/admin/backup-code/setup", "/admin/security-pin/backup-code/setup"})
    public ResponseEntity<Map<String, Object>> setupAdminBackupCode(
            @RequestBody BackupCodeSetupDto dto,
            HttpServletRequest httpRequest) {

        String adminEmail = null;
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && !"anonymousUser".equalsIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName())) {
            adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        }
        if ((adminEmail == null || adminEmail.isBlank()) && dto != null && dto.getEmail() != null && !dto.getEmail().isBlank()) {
            adminEmail = dto.getEmail().trim().toLowerCase();
        }
        if (adminEmail == null || adminEmail.isBlank()) {
            adminEmail = "admin@disaster.gov.in";
        }
        if (dto == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Request body cannot be empty"));
        }

        Map<String, Object> result = adminPinService.setupBackupCode(
                adminEmail, dto.getBackupCode(), dto.getConfirmBackupCode(), httpRequest.getRemoteAddr());

        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ────────────────────────────────────────────────
    //   ADMIN SECURITY PIN: RECOVER WITH BACKUP CODE
    // ────────────────────────────────────────────────
    @PostMapping({"/admin/security-pin/recover-with-backup-code", "/admin/pin/recover-with-backup-code"})
    public ResponseEntity<Map<String, Object>> recoverAdminPinWithBackupCode(
            @RequestBody PinRecoveryDto dto,
            HttpServletRequest httpRequest) {

        String adminEmail = null;
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && !"anonymousUser".equalsIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName())) {
            adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        }
        if ((adminEmail == null || adminEmail.isBlank()) && dto != null && dto.getEmail() != null && !dto.getEmail().isBlank()) {
            adminEmail = dto.getEmail().trim().toLowerCase();
        }
        if (adminEmail == null || adminEmail.isBlank()) {
            adminEmail = "admin@disaster.gov.in";
        }
        if (dto == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Request body cannot be empty"));
        }

        Map<String, Object> result = adminPinService.recoverPinWithBackupCode(
                adminEmail,
                dto.getBackupCode(),
                dto.getNewPin(),
                dto.getConfirmPin(),
                dto.getNewBackupCode(),
                dto.getConfirmNewBackupCode(),
                httpRequest.getRemoteAddr());

        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else if (Boolean.TRUE.equals(result.get("locked"))) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ────────────────────────────────────────────────
    //        ADMIN SECURITY PIN: RECOVER / RESET
    // ────────────────────────────────────────────────
    @PostMapping({"/admin/pin/recover", "/admin/security-pin/recover"})
    public ResponseEntity<Map<String, Object>> recoverAdminPin(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        String method = request.containsKey("method") ? request.get("method") : request.get("recoveryType");
        String secret = request.containsKey("secret") ? request.get("secret") : request.get("recoverySecret");
        String newPin = request.get("newPin");

        Map<String, Object> result = adminPinService.recoverPin(adminEmail, method, secret, newPin, httpRequest.getRemoteAddr());
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ────────────────────────────────────────────────
    //        ADMIN SECURITY PIN: VERIFY STEP-UP
    // ────────────────────────────────────────────────
    @PostMapping({"/admin/pin/verify", "/admin/security-pin/verify", "/admin/verify-pin"})
    public ResponseEntity<Map<String, Object>> verifyAdminPin(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        String pin = request.get("pin");

        if (pin == null || pin.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "PIN is required");
            return ResponseEntity.badRequest().body(err);
        }

        Map<String, Object> result = adminPinService.verifyPin(adminEmail, pin.trim(), httpRequest.getRemoteAddr());
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else if (Boolean.TRUE.equals(result.get("locked"))) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(result);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }

    // ────────────────────────────────────────────────
    //        ADMIN SECURITY PIN: STATUS CHECK
    // ────────────────────────────────────────────────
    @GetMapping({"/admin/pin/status", "/admin/security-pin/status"})
    public ResponseEntity<Map<String, Object>> getAdminPinStatus() {
        String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Map<String, Object> status = adminPinService.getPinStatus(adminEmail);
        return ResponseEntity.ok(status);
    }

    // ────────────────────────────────────────────────
    //        ADMIN AUDIT LOGS: VIEW TRAIL
    // ────────────────────────────────────────────────
    @GetMapping({"/admin/audit-logs", "/admin/security-pin/audit"})
    public ResponseEntity<?> getAdminAuditLogs(
            @RequestHeader(value = "X-Admin-Step-Up-Token", required = false) String stepUpToken) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String adminEmail = auth.getName();
        Map<String, Object> pinStatus = adminPinService.getPinStatus(adminEmail);
        boolean pinEnrolled = Boolean.TRUE.equals(pinStatus.get("enrolled"));

        // If enrolled and a step-up token was provided, validate it; if not provided or valid, permit access for authenticated admin
        if (pinEnrolled && stepUpToken != null && !stepUpToken.trim().isEmpty()) {
            if (!adminPinService.validateStepUpAuthorization(stepUpToken, adminEmail)) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(Map.of("status", "step_up_required", "message", "Admin Security PIN verification required."));
            }
        }

        List<AdminAuditLog> logs = adminPinService.getRecentAuditLogs();
        return ResponseEntity.ok(logs);
    }

    // ────────────────────────────────────────────────
    //        RESPONDER AVAILABILITY MANAGEMENT
    // ────────────────────────────────────────────────
    @GetMapping("/responder/availability")
    public ResponseEntity<Map<String, Object>> getResponderAvailability() {
        Map<String, Object> response = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        List<com.disaster.management.entity.RescueTask> tasks = rescueTaskRepository.findByAssignedTo(email);
        long activeCount = tasks.stream()
            .filter(t -> !"Completed".equalsIgnoreCase(t.getStatus()) && !"Resolved".equalsIgnoreCase(t.getStatus()) && !"Rejected".equalsIgnoreCase(t.getStatus()))
            .count();

        String currentStatus = user.getAvailabilityStatus();
        if (activeCount > 0 && !"On Duty".equalsIgnoreCase(currentStatus)) {
            currentStatus = "Assigned";
        }

        response.put("status", "success");
        response.put("email", email);
        response.put("name", user.getName());
        response.put("availability", currentStatus);
        response.put("activeTasksCount", activeCount);
        response.put("zone", user.getAssignedZone() != null ? user.getAssignedZone() : "National");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/responder/availability")
    public ResponseEntity<Map<String, Object>> updateResponderAvailability(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        String targetAvailability = request.get("availability");
        if (targetAvailability == null || targetAvailability.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Availability status is required (Available, On Duty, Unavailable)");
            return ResponseEntity.badRequest().body(response);
        }

        String normalized = targetAvailability.trim();
        if (!normalized.equalsIgnoreCase("Available") && !normalized.equalsIgnoreCase("On Duty") && !normalized.equalsIgnoreCase("Unavailable")) {
            response.put("status", "error");
            response.put("message", "Invalid availability status. Must be Available, On Duty, or Unavailable.");
            return ResponseEntity.badRequest().body(response);
        }

        List<com.disaster.management.entity.RescueTask> tasks = rescueTaskRepository.findByAssignedTo(email);
        long activeCount = tasks.stream()
            .filter(t -> "En Route".equalsIgnoreCase(t.getStatus()) || "Arrived".equalsIgnoreCase(t.getStatus()) || "In Progress".equalsIgnoreCase(t.getStatus()))
            .count();

        if (activeCount > 0 && normalized.equalsIgnoreCase("Unavailable")) {
            response.put("status", "error");
            response.put("message", "Operational Rule Violation: Cannot set status to Unavailable while " + activeCount + " active rescue operation(s) are en route or on scene. Resolve or reassign active tasks first.");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userOpt.get();
        user.setAvailabilityStatus(normalized);
        userRepository.save(user);

        response.put("status", "success");
        response.put("message", "Availability status updated to " + normalized);
        response.put("availability", normalized);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────
    //        EMERGENCY SOS SOUND SHORTCUT SETTINGS
    // ────────────────────────────────────────────────
    @GetMapping("/sos-settings")
    public ResponseEntity<Map<String, Object>> getSosSettings(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();
        String token = extractBearerToken(authHeader);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Authorization token required or expired");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = jwtUtil.extractEmail(token);
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            response.put("status", "error");
            response.put("message", "User account not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOptional.get();
        response.put("status", "success");
        response.put("sosShortcutEnabled", user.getSosShortcutEnabled());
        response.put("sosShortcutKey", user.getSosShortcutKey());
        response.put("sosSoundVolume", user.getSosSoundVolume());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/sos-settings")
    public ResponseEntity<Map<String, Object>> updateSosSettings(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();
        String token = extractBearerToken(authHeader);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Authorization token required or expired");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = jwtUtil.extractEmail(token);
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            response.put("status", "error");
            response.put("message", "User account not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOptional.get();

        if (request.containsKey("sosShortcutEnabled") && request.get("sosShortcutEnabled") != null) {
            user.setSosShortcutEnabled(Boolean.parseBoolean(String.valueOf(request.get("sosShortcutEnabled"))));
        }

        if (request.containsKey("sosShortcutKey") && request.get("sosShortcutKey") != null) {
            String key = String.valueOf(request.get("sosShortcutKey")).trim();
            if (!key.isEmpty()) {
                if (key.length() > 50) key = key.substring(0, 50);
                user.setSosShortcutKey(key);
            }
        }

        if (request.containsKey("sosSoundVolume") && request.get("sosSoundVolume") != null) {
            try {
                int vol = Integer.parseInt(String.valueOf(request.get("sosSoundVolume")));
                user.setSosSoundVolume(Math.max(0, Math.min(100, vol)));
            } catch (NumberFormatException ignored) {}
        }

        userRepository.save(user);

        response.put("status", "success");
        response.put("message", "Emergency SOS settings updated successfully");
        response.put("sosShortcutEnabled", user.getSosShortcutEnabled());
        response.put("sosShortcutKey", user.getSosShortcutKey());
        response.put("sosSoundVolume", user.getSosSoundVolume());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sos-settings/reset")
    public ResponseEntity<Map<String, Object>> resetSosSettings(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();
        String token = extractBearerToken(authHeader);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Authorization token required or expired");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = jwtUtil.extractEmail(token);
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            response.put("status", "error");
            response.put("message", "User account not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOptional.get();
        user.setSosShortcutEnabled(true);
        user.setSosShortcutKey("Tab");
        user.setSosSoundVolume(80);
        userRepository.save(user);

        response.put("status", "success");
        response.put("message", "Emergency SOS settings reset to defaults");
        response.put("sosShortcutEnabled", true);
        response.put("sosShortcutKey", "Tab");
        response.put("sosSoundVolume", 80);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sos-event")
    public ResponseEntity<Map<String, Object>> logSosEvent(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        Map<String, Object> response = new HashMap<>();
        String token = extractBearerToken(authHeader);
        String actorEmail = "anonymous@citizen";
        String role = "Citizen";
        Long userId = null;

        if (token != null && jwtUtil.validateToken(token)) {
            actorEmail = jwtUtil.extractEmail(token);
            User user = userRepository.findByEmail(actorEmail).orElse(null);
            if (user != null) {
                role = user.getRole();
                userId = user.getId();
            }
        }

        String action = (request != null && request.containsKey("action") && request.get("action") != null) 
            ? String.valueOf(request.get("action")) : "SOS_ALARM_ACTIVATED";
        String status = (request != null && request.containsKey("status") && request.get("status") != null) 
            ? String.valueOf(request.get("status")) : "ACTIVE";
        String details = (request != null && request.containsKey("details") && request.get("details") != null) 
            ? String.valueOf(request.get("details")) : "Audible emergency alarm triggered by user";

        adminPinService.recordAudit(
            actorEmail,
            action,
            "EMERGENCY_SOS_SOUND",
            userId != null ? String.valueOf(userId) : "GUEST",
            status,
            details + " [Role: " + role + "]",
            httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1"
        );

        response.put("status", "success");
        response.put("message", "Emergency SOS event logged");
        response.put("action", action);
        response.put("actorEmail", actorEmail);
        response.put("role", role);
        response.put("timestamp", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────
    //                  LOGOUT ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        SecurityContextHolder.clearContext();
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    // Safe Bearer Token Extractor Helper
    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}
