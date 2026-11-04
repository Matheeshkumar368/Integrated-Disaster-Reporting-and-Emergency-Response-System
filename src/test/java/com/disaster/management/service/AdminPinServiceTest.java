package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.disaster.management.entity.User;
import com.disaster.management.repository.AdminAuditLogRepository;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPinServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AdminPinService adminPinService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@disaster.gov.in");
        adminUser.setPassword("encodedPassword");
        adminUser.setRole("Admin");
        adminUser.setAdminPin("encodedPin");
        adminUser.setAdminPinEnrolled(true);
        adminUser.setPinFailedAttempts(0);
    }

    @Test
    @DisplayName("Verify valid PIN succeeds and returns step-up token")
    void testVerifyPinSuccess() {
        when(userRepository.findByEmail("admin@disaster.gov.in")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("123456", "encodedPin")).thenReturn(true);
        when(jwtUtil.generateAdminStepUpToken("admin@disaster.gov.in")).thenReturn("stepUpTokenXYZ");

        Map<String, Object> result = adminPinService.verifyPin("admin@disaster.gov.in", "123456", "127.0.0.1");

        assertTrue((Boolean) result.get("success"));
        assertEquals("stepUpTokenXYZ", result.get("stepUpToken"));
        assertEquals(0, adminUser.getPinFailedAttempts());
        verify(userRepository).save(adminUser);
    }

    @Test
    @DisplayName("Verify invalid PIN increments failure count and locks after 3 attempts")
    void testVerifyPinLockout() {
        when(userRepository.findByEmail("admin@disaster.gov.in")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrongPin", "encodedPin")).thenReturn(false);

        // Attempt 1
        Map<String, Object> res1 = adminPinService.verifyPin("admin@disaster.gov.in", "wrongPin", "127.0.0.1");
        assertFalse((Boolean) res1.get("success"));
        assertEquals(1, adminUser.getPinFailedAttempts());

        // Attempt 2
        Map<String, Object> res2 = adminPinService.verifyPin("admin@disaster.gov.in", "wrongPin", "127.0.0.1");
        assertFalse((Boolean) res2.get("success"));
        assertEquals(2, adminUser.getPinFailedAttempts());

        // Attempt 3 -> Triggers lockout
        Map<String, Object> res3 = adminPinService.verifyPin("admin@disaster.gov.in", "wrongPin", "127.0.0.1");
        assertFalse((Boolean) res3.get("success"));
        assertTrue((Boolean) res3.get("locked"));
        assertTrue(adminUser.isPinLocked());
    }

    @Test
    @DisplayName("Enroll PIN successfully when current password matches")
    void testEnrollPinSuccess() {
        when(userRepository.findByEmail("admin@disaster.gov.in")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("admin123", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("654321")).thenReturn("newEncodedPin");

        Map<String, Object> result = adminPinService.enrollPin("admin@disaster.gov.in", "admin123", "654321", "127.0.0.1");

        assertTrue((Boolean) result.get("success"));
        assertEquals("newEncodedPin", adminUser.getAdminPin());
        assertTrue(adminUser.getAdminPinEnrolled());
        verify(userRepository).save(adminUser);
    }
}

