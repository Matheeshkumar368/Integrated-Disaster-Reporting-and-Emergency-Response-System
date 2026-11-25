package com.disaster.management.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.disaster.management.dto.UserRegistrationDto;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserRegistrationDto validCitizenDto;

    @BeforeEach
    void setUp() {
        validCitizenDto = new UserRegistrationDto();
        validCitizenDto.setName("Ravi Kumar");
        validCitizenDto.setEmail("ravi.kumar@example.com");
        validCitizenDto.setPassword("StrongPass123");
        validCitizenDto.setPhone("+91 9876543210");
        validCitizenDto.setLocation("Chennai");
        validCitizenDto.setRole("Citizen");
    }

    @Test
    @DisplayName("Valid Citizen registration succeeds and hashes password")
    void testRegisterCitizenSuccess() {
        when(userRepository.findByEmail("ravi.kumar@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("StrongPass123")).thenReturn("hashedPass123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.registerUser(validCitizenDto);

        assertNotNull(user);
        assertEquals("ravi.kumar@example.com", user.getEmail());
        assertEquals("hashedPass123", user.getPassword());
        assertEquals("Citizen", user.getRole());
        assertEquals("+91 9876543210", user.getPhone());
        assertEquals("Chennai", user.getLocation());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Admin self-registration is strictly forbidden")
    void testRegisterAdminForbidden() {
        validCitizenDto.setRole("Admin");

        SecurityException ex = assertThrows(SecurityException.class, () -> userService.registerUser(validCitizenDto));
        assertEquals("Administrative accounts cannot be self-registered", ex.getMessage());
    }

    @Test
    @DisplayName("Duplicate email registration is rejected with conflict")
    void testDuplicateEmailRejected() {
        when(userRepository.findByEmail("ravi.kumar@example.com")).thenReturn(Optional.of(new User()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.registerUser(validCitizenDto));
        assertEquals("Email already registered", ex.getMessage());
    }

    @Test
    @DisplayName("Invalid phone number format is rejected")
    void testInvalidPhoneRejected() {
        validCitizenDto.setPhone("invalid-phone");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.registerUser(validCitizenDto));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Password less than 6 characters is rejected")
    void testShortPasswordRejected() {
        validCitizenDto.setPassword("12345");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.registerUser(validCitizenDto));
        assertEquals("Password must be at least 6 characters long", ex.getMessage());
    }

    @Test
    @DisplayName("Responder registration sets Responder role and zone correctly")
    void testRegisterResponderSuccess() {
        UserRegistrationDto responderDto = new UserRegistrationDto();
        responderDto.setName("Officer Anitha");
        responderDto.setEmail("anitha.responder@disaster.gov.in");
        responderDto.setPassword("ResponderSecure999");
        responderDto.setPhone("+91 9123456780");
        responderDto.setLocation("Coimbatore");
        responderDto.setRole("Responder");
        responderDto.setAssignedZone("Coimbatore Central");
        responderDto.setGroupClassification("Leader");

        when(userRepository.findByEmail("anitha.responder@disaster.gov.in")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ResponderSecure999")).thenReturn("hashedResponderPass");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User responder = userService.registerUser(responderDto);

        assertNotNull(responder);
        assertEquals("Responder", responder.getRole());
        assertEquals("Coimbatore Central", responder.getAssignedZone());
        assertEquals("Leader", responder.getGroupClassification());
        assertEquals("Available", responder.getAvailabilityStatus());
    }
}

