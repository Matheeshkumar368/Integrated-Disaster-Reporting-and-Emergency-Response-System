package com.disaster.management.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.disaster.management.entity.IncidentMedia;
import com.disaster.management.entity.IncidentTimelineEvent;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.IncidentMediaRepository;
import com.disaster.management.repository.IncidentTimelineEventRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.TrackingSessionRepository;
import com.disaster.management.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TrackingAndMediaServiceTest {

    @Mock
    private IncidentMediaRepository mediaRepository;

    @Mock
    private IncidentTimelineEventRepository timelineRepository;

    @Mock
    private IncidentTimelineService timelineService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RescueTaskRepository taskRepository;

    @Mock
    private TrackingSessionRepository sessionRepository;

    @InjectMocks
    private MediaStorageService mediaStorageService;

    @InjectMocks
    private SmartAssignmentService smartAssignmentService;

    @InjectMocks
    private IncidentTimelineService incidentTimelineService;

    private RouteEstimationService routeEstimationService;

    @BeforeEach
    void setUp() {
        routeEstimationService = new RouteEstimationService();
        ReflectionTestUtils.setField(routeEstimationService, "osrmBaseUrl", "https://router.project-osrm.org/route/v1/driving");
        ReflectionTestUtils.setField(routeEstimationService, "timeoutMs", 1000);

        ReflectionTestUtils.setField(smartAssignmentService, "routeService", routeEstimationService);

        ReflectionTestUtils.setField(mediaStorageService, "uploadDirStr", "target/test-uploads");
        mediaStorageService.init();
    }

    @Test
    @DisplayName("RouteEstimationService - Haversine distance calculation produces accurate results")
    void testHaversineDistance() {
        // Distance between Chennai (13.0827, 80.2707) and Coimbatore (11.0168, 76.9558) is ~420-430 km
        double distKm = routeEstimationService.calculateDistanceKm(13.0827, 80.2707, 11.0168, 76.9558);
        assertTrue(distKm > 400.0 && distKm < 450.0, "Expected Chennai-Coimbatore distance ~426 km, got: " + distKm);

        Map<String, Object> fallback = routeEstimationService.calculateHaversineFallback(13.0827, 80.2707, 13.0850, 80.2720);
        assertEquals("success", fallback.get("status"));
        assertNotNull(fallback.get("etaText"));
        assertNotNull(fallback.get("coordinates"));
    }

    @Test
    @DisplayName("MediaStorageService - Stores valid JPEG image with metadata")
    void testStoreValidImage() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", "damage_photo.jpg", "image/jpeg", "fake-image-bytes".getBytes()
        );

        when(mediaRepository.save(any(IncidentMedia.class))).thenAnswer(invocation -> {
            IncidentMedia m = invocation.getArgument(0);
            m.setId(101L);
            return m;
        });

        IncidentMedia saved = mediaStorageService.storeMedia(
            file, 12L, "CITIZEN_REPORT", "citizen@gmail.com", "John Citizen",
            "Citizen", 13.0827, 80.2707, 15.0, "Flooding in neighborhood"
        );

        assertNotNull(saved);
        assertEquals("PHOTO", saved.getMediaType());
        assertEquals("damage_photo.jpg", saved.getOriginalFilename());
        assertTrue(saved.getStoredFilename().endsWith("damage_photo.jpg"));
        verify(mediaRepository).save(any(IncidentMedia.class));
    }

    @Test
    @DisplayName("MediaStorageService - Rejects forbidden executable file extensions")
    void testRejectForbiddenExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "malicious_script.exe", "application/x-msdownload", "evil-bytes".getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            mediaStorageService.storeMedia(
                file, null, "CITIZEN_REPORT", "user@test.com", "User",
                "Citizen", null, null, null, null
            );
        });

        assertTrue(ex.getMessage().contains("not allowed for security reasons"));
    }

    @Test
    @DisplayName("SmartAssignmentService - Correctly prioritizes available nearby responders")
    void testSmartAssignmentRecommendations() {
        RescueTask task = new RescueTask();
        task.setTaskId(501L);
        task.setTitle("Severe Flash Flood Rescue");
        task.setPriority("High");
        task.setZone("Chennai");
        task.setLatitude(13.0827);
        task.setLongitude(80.2707);

        when(taskRepository.findById(501L)).thenReturn(Optional.of(task));

        User responder1 = new User();
        responder1.setId(1L);
        responder1.setEmail("rescuer@disaster.gov.in");
        responder1.setName("Ravi Kumar");
        responder1.setRole("Responder");
        responder1.setAssignedZone("Chennai");
        responder1.setGroupClassification("Rescuer");

        User responder2 = new User();
        responder2.setId(2L);
        responder2.setEmail("busy@disaster.gov.in");
        responder2.setName("Suresh Raina");
        responder2.setRole("Responder");
        responder2.setAssignedZone("Coimbatore");
        responder2.setGroupClassification("Helper");

        when(userRepository.findByRole("Responder")).thenReturn(List.of(responder1, responder2));
        // responder1 has 0 active tasks
        when(taskRepository.countByAssignedToAndStatusIn(eq("rescuer@disaster.gov.in"), anyList())).thenReturn(0L);
        // responder2 has 3 active tasks
        when(taskRepository.countByAssignedToAndStatusIn(eq("busy@disaster.gov.in"), anyList())).thenReturn(3L);

        List<Map<String, Object>> recs = smartAssignmentService.getRecommendationsForTask(501L);

        assertNotNull(recs);
        assertEquals(2, recs.size());

        Map<String, Object> top = recs.get(0);
        assertEquals("rescuer@disaster.gov.in", top.get("responderEmail"));
        assertEquals(1, top.get("rank"));
        assertTrue((Integer) top.get("matchScore") > (Integer) recs.get(1).get("matchScore"),
            "Local available rescuer should outrank distant busy responder");
    }

    @Test
    @DisplayName("IncidentTimelineService - Logs timeline events and chat messages")
    void testIncidentTimelineEvents() {
        when(timelineRepository.save(any(IncidentTimelineEvent.class))).thenAnswer(inv -> {
            IncidentTimelineEvent e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        IncidentTimelineEvent event = incidentTimelineService.postChatMessage(
            15L, "CITIZEN_REPORT", "responder@disaster.gov.in", "Responder", "Emergency Responder", "En route to location with boat team."
        );

        assertNotNull(event);
        assertEquals("MESSAGE", event.getEventType());
        assertEquals("En route to location with boat team.", event.getMessage());
        assertEquals(15L, event.getIncidentId());
        verify(timelineRepository).save(any(IncidentTimelineEvent.class));
    }
}

