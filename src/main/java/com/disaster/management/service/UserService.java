package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.dto.UserRegistrationDto;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String PHONE_REGEX = "^[+]?[0-9\\s\\-\\(\\)]{7,20}$";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Registers a new Citizen or Responder.
     * Prevents administrative self-registration.
     * Enforces required field, phone, email, and password validation.
     */
    @Transactional
    public User registerUser(UserRegistrationDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Registration data cannot be null");
        }

        // 1. Required fields presence check
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Full legal name is required");
        }
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required");
        }
        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (dto.getPhone() == null || dto.getPhone().trim().isEmpty()) {
            throw new IllegalArgumentException("Contact phone number is required");
        }
        if (dto.getLocation() == null || dto.getLocation().trim().isEmpty()) {
            throw new IllegalArgumentException("Location / City is required");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        String normalizedPhone = dto.getPhone().trim();
        String name = dto.getName().trim();
        String rawPassword = dto.getPassword();
        String location = dto.getLocation().trim();
        String requestedRole = dto.getRole() != null ? dto.getRole().trim() : "Citizen";

        // 2. Format validations
        if (!normalizedEmail.matches(EMAIL_REGEX)) {
            throw new IllegalArgumentException("Please provide a valid email address");
        }
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if (!normalizedPhone.matches(PHONE_REGEX)) {
            throw new IllegalArgumentException("Please provide a valid phone number (e.g. +91 98765 43210)");
        }

        // 3. Prevent public self-escalation to Administrator
        if ("Admin".equalsIgnoreCase(requestedRole) || "Administrator".equalsIgnoreCase(requestedRole) || "ROLE_ADMIN".equalsIgnoreCase(requestedRole)) {
            throw new SecurityException("Administrative accounts cannot be self-registered");
        }

        // 4. Duplicate email check
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("Email already registered");
        }

        // 5. Construct and populate new User entity
        User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(normalizedPhone);
        user.setLocation(location);
        user.setCreatedAt(LocalDateTime.now());
        user.setAvailabilityStatus("Available");

        if ("Responder".equalsIgnoreCase(requestedRole)) {
            user.setRole("Responder");
            user.setAssignedZone(dto.getAssignedZone() != null && !dto.getAssignedZone().trim().isEmpty() ? dto.getAssignedZone().trim() : location);
            user.setGroupClassification(dto.getGroupClassification() != null && !dto.getGroupClassification().trim().isEmpty() ? dto.getGroupClassification().trim() : "Rescuer");
            user.setAdditionalDetails(dto.getAdditionalDetails());
        } else {
            user.setRole("Citizen");
        }

        User savedUser = userRepository.save(user);
        log.info("New {} successfully registered: {}", savedUser.getRole(), savedUser.getEmail());
        return savedUser;
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public boolean existsByEmail(String email) {
        if (email == null) return false;
        return userRepository.findByEmail(email.trim().toLowerCase()).isPresent();
    }
}

