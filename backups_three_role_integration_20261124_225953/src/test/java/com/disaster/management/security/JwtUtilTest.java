package com.disaster.management.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "test-secret-key-for-testing-purposes-must-be-long-enough-2026-disaster";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(secret, 3600000, 15);
    }

    @Test
    @DisplayName("Generate access token and extract subject and role")
    void testGenerateAndExtractToken() {
        String token = jwtUtil.generateToken("admin@disaster.gov.in", "Admin", "Admin User");

        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("admin@disaster.gov.in", jwtUtil.extractEmail(token));
        assertEquals("Admin", jwtUtil.extractRole(token));
        assertFalse(jwtUtil.isStepUpToken(token));
    }

    @Test
    @DisplayName("Generate admin step-up token and verify step-up claim")
    void testAdminStepUpToken() {
        String stepUpToken = jwtUtil.generateAdminStepUpToken("admin@disaster.gov.in");

        assertNotNull(stepUpToken);
        assertTrue(jwtUtil.validateToken(stepUpToken));
        assertTrue(jwtUtil.isStepUpToken(stepUpToken));
        assertEquals("Admin", jwtUtil.extractRole(stepUpToken));
    }

    @Test
    @DisplayName("Invalid token validation returns false")
    void testInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.structure"));
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken(null));
    }
}

