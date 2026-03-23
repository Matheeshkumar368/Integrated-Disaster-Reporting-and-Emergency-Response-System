package com.disaster.management.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081"})
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ────────────────────────────────────────────────
    //                  REGISTER ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody User user) {

        Map<String, String> response = new HashMap<>();

        // Basic validation
        if (user.getEmail() == null || user.getPassword() == null) {
            response.put("status", "error");
            response.put("message", "Email and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        // Check if user already exists
        Optional<User> existing = userRepository.findByEmail(user.getEmail());
        if (existing.isPresent()) {
            response.put("status", "error");
            response.put("message", "Email already registered");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        try {
            // Hash the password before saving
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            response.put("status", "success");
            response.put("message", "User Registered Successfully!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Registration failed: " + e.getMessage());
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

        // Debug log – will appear in console
        System.out.println("LOGIN ATTEMPT → Email: [REDACTED]");

        // Validate input
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Email and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        // Find user
        Optional<User> userOptional = userRepository.findByEmail(email.trim());

        System.out.println("User found in database: " + userOptional.isPresent());

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (passwordEncoder.matches(password, user.getPassword())) {
                try {
                    // Generate JWT with role
                    String token = jwtUtil.generateToken(email, user.getRole());
                    response.put("status", "success");
                    response.put("message", "Login successful");
                    response.put("token", token);
                    response.put("role", user.getRole());
                    response.put("name", user.getName());

                    System.out.println("LOGIN SUCCESS → Token generated for role: " + user.getRole());
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    System.out.println("JWT generation failed: " + e.getMessage());
                    response.put("status", "error");
                    response.put("message", "Failed to generate token");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            } else {
                System.out.println("LOGIN FAILED → Password mismatch");
                response.put("status", "error");
                response.put("message", "Invalid password");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } else {
            System.out.println("LOGIN FAILED → User not found");
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }


    // ────────────────────────────────────────────────
    //              GET USER PROFILE ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        // Extract token from Authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("status", "error");
            response.put("message", "Authorization token required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = authHeader.substring(7);

        // Validate token and extract email from JWT
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = jwtUtil.extractEmail(token);

        // Get user by email from JWT token
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            response.put("status", "success");
            response.put("name", user.getName());
            response.put("email", user.getEmail());
            response.put("phone", user.getPhone());
            response.put("location", user.getLocation());
            response.put("role", user.getRole());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // ────────────────────────────────────────────────
    //              UPDATE USER PROFILE ENDPOINT
    // ────────────────────────────────────────────────
    @PostMapping("/update-profile")
    public ResponseEntity<Map<String, String>> updateProfile(@RequestBody Map<String, String> request, @RequestHeader("Authorization") String authHeader) {
        Map<String, String> response = new HashMap<>();

        // Extract token from Authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("status", "error");
            response.put("message", "Authorization token required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = authHeader.substring(7);

        // Validate token and extract email from JWT
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String authenticatedEmail = jwtUtil.extractEmail(token);

        // Get userId from request
        String userIdStr = request.get("userId");
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "User ID is required");
            return ResponseEntity.badRequest().body(response);
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            response.put("status", "error");
            response.put("message", "Invalid user ID format");
            return ResponseEntity.badRequest().body(response);
        }

        // Verify the authenticated user owns this profile
        Optional<User> authenticatedUserOptional = userRepository.findByEmail(authenticatedEmail);
        if (!authenticatedUserOptional.isPresent()) {
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User authenticatedUser = authenticatedUserOptional.get();
        if (!authenticatedUser.getId().equals(userId)) {
            response.put("status", "error");
            response.put("message", "Access denied: You can only update your own profile");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Get the user to update
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Update fields if provided
            if (request.containsKey("name") && request.get("name") != null) {
                user.setName(request.get("name"));
            }
            if (request.containsKey("phone") && request.get("phone") != null) {
                user.setPhone(request.get("phone"));
            }
            if (request.containsKey("location") && request.get("location") != null) {
                user.setLocation(request.get("location"));
            }
            if (request.containsKey("password") && request.get("password") != null && !request.get("password").isEmpty()) {
                user.setPassword(passwordEncoder.encode(request.get("password")));
            }

            try {
                userRepository.save(user);
                response.put("status", "success");
                response.put("message", "Profile updated successfully");
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", "Failed to update profile: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } else {
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // Admin: Assign zone to responder
    @PostMapping("/assign-zone")
    public ResponseEntity<Map<String, String>> assignZone(@RequestBody Map<String, String> request, @RequestHeader("Authorization") String authHeader) {
        Map<String, String> response = new HashMap<>();
        
        // Extract token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("status", "error");
            response.put("message", "Authorization token required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = authHeader.substring(7);
        
        // Validate token and check admin role
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String adminEmail = jwtUtil.extractEmail(token);
        String adminRole = jwtUtil.extractRole(token);
        
        if (!"Admin".equals(adminRole)) {
            response.put("status", "error");
            response.put("message", "Access denied. Admin only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        // Get responder and zone from request
        String responderEmail = request.get("responderEmail");
        String zone = request.get("zone");
        
        if (responderEmail == null || responderEmail.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Responder email is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Find responder
        Optional<User> responderOpt = userRepository.findByEmail(responderEmail);
        if (!responderOpt.isPresent()) {
            response.put("status", "error");
            response.put("message", "Responder not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        User responder = responderOpt.get();
        if (!"Responder".equals(responder.getRole())) {
            response.put("status", "error");
            response.put("message", "User is not a responder");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        // Update zone
        responder.setAssignedZone(zone);
        userRepository.save(responder);
        
        response.put("status", "success");
        response.put("message", "Zone assigned successfully");
        return ResponseEntity.ok(response);
    }

}