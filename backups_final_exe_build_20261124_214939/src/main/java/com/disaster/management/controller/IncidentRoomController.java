package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Alert;
import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.IncidentMedia;
import com.disaster.management.entity.IncidentTimelineEvent;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.AlertRepository;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.service.IncidentTimelineService;
import com.disaster.management.service.MediaStorageService;

@RestController
@RequestMapping("/api/incidents")
@CrossOrigin(origins = {"*"})
public class IncidentRoomController {

    private static final Logger log = LoggerFactory.getLogger(IncidentRoomController.class);

    @Autowired
    private IncidentTimelineService timelineService;

    @Autowired
    private MediaStorageService mediaService;

    @Autowired
    private CitizenReportRepository reportRepository;

    @Autowired
    private RescueTaskRepository taskRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    private String normalizeType(String type) {
        if (type == null) return "CITIZEN_REPORT";
        String upper = type.trim().toUpperCase();
        if ("TASK".equals(upper) || "RESCUE_TASK".equals(upper)) return "RESCUE_TASK";
        if ("ALERT".equals(upper) || "ALERT_BROADCAST".equals(upper)) return "ALERT_BROADCAST";
        return "CITIZEN_REPORT";
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<IncidentTimelineEvent>> getIncidentTimeline(
            @PathVariable Long id,
            @RequestParam(value = "type", defaultValue = "CITIZEN_REPORT") String type) {
        String normType = normalizeType(type);
        return ResponseEntity.ok(timelineService.getTimeline(id, normType));
    }

    @PostMapping("/{id}/timeline/message")
    public ResponseEntity<?> postTimelineMessage(
            @PathVariable Long id,
            @RequestParam(value = "type", defaultValue = "CITIZEN_REPORT") String type,
            @RequestBody Map<String, String> body) {

        String normType = normalizeType(type);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        String text = body.get("message");
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Message text is required"));
        }

        String userEmail = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse("Citizen");

        String userName = userEmail;
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isPresent() && userOpt.get().getName() != null) {
            userName = userOpt.get().getName();
        }

        try {
            IncidentTimelineEvent event = timelineService.postChatMessage(
                id, normType, userEmail, role, userName, text
            );
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "event", event
            ));
        } catch (Exception e) {
            log.error("Failed to post message on incident room: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // Delete own chat message (Author or Admin)
    @DeleteMapping("/timeline/message/{eventId}")
    public ResponseEntity<?> deleteTimelineMessage(@PathVariable Long eventId) {
        return executeDeleteTimelineMessage(eventId);
    }

    @DeleteMapping("/{id}/timeline/message/{eventId}")
    public ResponseEntity<?> deleteTimelineMessageWithId(@PathVariable Long id, @PathVariable Long eventId) {
        return executeDeleteTimelineMessage(eventId);
    }

    private ResponseEntity<?> executeDeleteTimelineMessage(Long eventId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        String callerEmail = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));

        try {
            timelineService.deleteMessage(eventId, callerEmail, isAdmin);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Chat message deleted successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete timeline message #{}: {}", eventId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Failed to delete message: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/room")
    public ResponseEntity<?> getIncidentRoomData(
            @PathVariable Long id,
            @RequestParam(value = "type", defaultValue = "CITIZEN_REPORT") String type) {

        String normType = normalizeType(type);
        Map<String, Object> room = new HashMap<>();
        room.put("incidentId", id);
        room.put("incidentType", normType);

        if ("RESCUE_TASK".equalsIgnoreCase(normType)) {
            Optional<RescueTask> taskOpt = taskRepository.findById(id);
            taskOpt.ifPresent(task -> room.put("task", task));
        } else if ("ALERT_BROADCAST".equalsIgnoreCase(normType)) {
            Optional<Alert> alertOpt = alertRepository.findById(id);
            alertOpt.ifPresent(alert -> room.put("alert", alert));
        } else {
            Optional<CitizenReport> reportOpt = reportRepository.findById(id);
            reportOpt.ifPresent(report -> room.put("report", report));
        }

        List<IncidentTimelineEvent> timeline = timelineService.getTimeline(id, normType);
        List<IncidentMedia> mediaList = mediaService.getMediaByIncidentAndType(id, normType);

        room.put("timeline", timeline);
        room.put("media", mediaList);

        return ResponseEntity.ok(room);
    }
}

