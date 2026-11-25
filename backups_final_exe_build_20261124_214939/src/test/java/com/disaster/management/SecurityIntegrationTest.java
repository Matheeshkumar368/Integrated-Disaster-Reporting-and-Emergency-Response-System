package com.disaster.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("Public endpoint: Broadcasted alerts allow unauthenticated access")
    void testPublicBroadcastedAlerts() throws Exception {
        mockMvc.perform(get("/api/alerts/broadcasted"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public endpoint: Weather endpoint responds without authentication")
    void testPublicWeatherEndpoint() throws Exception {
        mockMvc.perform(get("/api/weather").param("city", "Chennai"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.city").value("Chennai"));
    }

    @Test
    @DisplayName("Public endpoint: Live disasters list accessible publicly")
    void testPublicDisastersEndpoint() throws Exception {
        mockMvc.perform(get("/api/disasters"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoint: POST /api/alerts rejects unauthenticated requests")
    void testProtectedAdminEndpointUnauthorized() throws Exception {
        mockMvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Cyclone Warning\",\"message\":\"Evacuate\",\"region\":\"Chennai\",\"severity\":\"High\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authentication: Invalid login credentials return generic error message")
    void testInvalidLoginProducesGenericMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nonexistent@disaster.gov.in\",\"password\":\"wrongpass\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Actuator: Health endpoint returns UP status for deployment probes")
    void testActuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
