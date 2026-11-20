package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.ResponderLocation;
import com.disaster.management.entity.TrackingSession;
import com.disaster.management.entity.User;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.ResponderLocationRepository;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.service.ResponderTrackingService;

@RestController
@RequestMapping("/api/tracking")
@CrossOrigin(origins = {"*"})
public class TrackingController {

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);

    @Autowired
    private ResponderTrackingService trackingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RescueTaskRepository rescueTaskRepository;

    @Autowired
    private CitizenReportRepository citizenReportRepository;

    @Autowired
    private ResponderLocationRepository locationRepository;

    @PostMapping("/start")
    public ResponseEntity<?> startTracking(@RequestBody Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        Long assignmentId = getLongValue(req.get("assignmentId"));
        if (assignmentId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "assignmentId is required"));
        }

        Double lat = getDoubleValue(req.get("latitude"));
        Double lng = getDoubleValue(req.get("longitude"));
        Double accuracy = getDoubleValue(req.get("accuracy"));

        try {
            validateCoordinates(lat, lng);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }

        String userEmail = auth.getName();
        String userName = userEmail;
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isPresent() && userOpt.get().getName() != null) {
            userName = userOpt.get().getName();
        }

        try {
            TrackingSession session = trackingService.startTracking(
                assignmentId, userEmail, userName, lat, lng, accuracy
            );
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Tracking session activated.",
                "sessionId", session.getId(),
                "assignmentId", session.getAssignmentId()
            ));
        } catch (Exception e) {
            log.error("Failed to start tracking session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateLocation(@RequestBody Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        Long assignmentId = getLongValue(req.get("assignmentId"));
        Double lat = getDoubleValue(req.get("latitude"));
        Double lng = getDoubleValue(req.get("longitude"));

        if (assignmentId == null || lat == null || lng == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", "assignmentId, latitude, and longitude are required"));
        }

        try {
            validateCoordinates(lat, lng);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }

        Double accuracy = getDoubleValue(req.get("accuracy"));
        Double heading = getDoubleValue(req.get("heading"));
        Double speed = getDoubleValue(req.get("speed"));

        try {
            TrackingSession session = trackingService.updateLocation(
                assignmentId, auth.getName(), lat, lng, accuracy, heading, speed
            );
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "assignmentId", session.getAssignmentId(),
                "latitude", session.getCurrentLatitude(),
                "longitude", session.getCurrentLongitude()
            ));
        } catch (Exception e) {
            log.error("Failed to update telemetry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopTracking(@RequestBody Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        Long assignmentId = getLongValue(req.get("assignmentId"));
        if (assignmentId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "assignmentId is required"));
        }

        try {
            TrackingSession session = trackingService.stopTracking(assignmentId, auth.getName());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Tracking session concluded."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping(value = "/stream/{assignmentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTracking(@PathVariable Long assignmentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
            boolean isResponder = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Responder"));
            if (!isAdmin && !isResponder) {
                if (!isCitizenAuthorizedForTracking(auth.getName(), assignmentId)) {
                    SseEmitter emitter = new SseEmitter(5000L);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("message", "Access denied: Unauthorized tracking request.")));
                        emitter.complete();
                    } catch (Exception ignored) {}
                    return emitter;
                }
            }
        }
        return trackingService.subscribe(assignmentId);
    }

    @GetMapping("/latest/{assignmentId}")
    public ResponseEntity<?> getLatestTelemetry(@PathVariable Long assignmentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        boolean isResponder = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Responder"));

        if (!isAdmin && !isResponder) {
            if (!isCitizenAuthorizedForTracking(auth.getName(), assignmentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "error", "message", "Access denied: You can only track responders assigned to your own emergency reports."));
            }
        }

        Map<String, Object> telemetry = trackingService.getLatestLocation(assignmentId);
        return ResponseEntity.ok(telemetry);
    }

    @GetMapping("/history/{assignmentId}")
    public ResponseEntity<?> getLocationHistory(@PathVariable Long assignmentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        boolean isResponder = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Responder"));

        if (!isAdmin && !isResponder) {
            if (!isCitizenAuthorizedForTracking(auth.getName(), assignmentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "error", "message", "Access denied: Unauthorized tracking history request."));
            }
        }

        Long targetTaskId = assignmentId;
        Optional<RescueTask> taskOpt = rescueTaskRepository.findById(assignmentId);
        if (taskOpt.isEmpty()) {
            List<RescueTask> linked = rescueTaskRepository.findByCitizenReportId(assignmentId);
            if (!linked.isEmpty()) {
                targetTaskId = linked.get(0).getTaskId();
            }
        }

        List<ResponderLocation> list = locationRepository.findByAssignmentIdOrderByRecordedAtAsc(targetTaskId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('Admin', 'Responder')")
    public ResponseEntity<List<Map<String, Object>>> getActiveSessions() {
        return ResponseEntity.ok(trackingService.getActiveSessionsForRadar());
    }

    private void validateCoordinates(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Latitude and longitude must not be null.");
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0 degrees.");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0 degrees.");
        }
        if (lat == 0.0 && lng == 0.0) {
            throw new IllegalArgumentException("Coordinates (0.0, 0.0) are invalid.");
        }
    }

    private boolean isCitizenAuthorizedForTracking(String citizenEmail, Long assignmentId) {
        if (citizenEmail == null || assignmentId == null) return false;

        // 1. Is assignmentId a CitizenReport ID?
        Optional<CitizenReport> repOpt = citizenReportRepository.findById(assignmentId);
        if (repOpt.isPresent()) {
            return citizenEmail.equalsIgnoreCase(repOpt.get().getCitizenEmail());
        }

        // 2. Is assignmentId a RescueTask ID?
        Optional<RescueTask> taskOpt = rescueTaskRepository.findById(assignmentId);
        if (taskOpt.isPresent()) {
            RescueTask task = taskOpt.get();
            if (task.getCitizenReportId() != null) {
                Optional<CitizenReport> linkedRep = citizenReportRepository.findById(task.getCitizenReportId());
                if (linkedRep.isPresent()) {
                    return citizenEmail.equalsIgnoreCase(linkedRep.get().getCitizenEmail());
                }
            }
        }
        return false;
    }

    private Long getLongValue(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double getDoubleValue(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
