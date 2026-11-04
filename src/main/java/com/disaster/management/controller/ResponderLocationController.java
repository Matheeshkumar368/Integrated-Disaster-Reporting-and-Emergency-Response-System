package com.disaster.management.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.ResponderLocation;
import com.disaster.management.service.ResponderLocationService;

@RestController
@RequestMapping("/api/responder/location")
@CrossOrigin(origins = {"*"})
@PreAuthorize("hasAnyRole('Responder', 'Admin')")
public class ResponderLocationController {

    private static final Logger log = LoggerFactory.getLogger(ResponderLocationController.class);

    @Autowired
    private ResponderLocationService locationService;

    /**
     * Start Live GPS Sharing
     * POST /api/responder/location/start
     */
    @PostMapping("/start")
    public ResponseEntity<?> startLiveLocation(@RequestBody Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Authentication required."));
        }

        String responderEmail = auth.getName();
        Long taskId = getLongValue(req.get("taskId") != null ? req.get("taskId") : req.get("assignmentId"));
        Double lat = getDoubleValue(req.get("latitude"));
        Double lng = getDoubleValue(req.get("longitude"));
        Double accuracy = getDoubleValue(req.get("accuracy"));

        try {
            ResponderLocation saved = locationService.startLocationSharing(taskId, responderEmail, lat, lng, accuracy);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Live GPS telemetry activated.",
                "locationId", saved.getId(),
                "responderId", saved.getResponderId() != null ? saved.getResponderId() : 0L,
                "taskId", saved.getAssignmentId(),
                "latitude", saved.getLatitude(),
                "longitude", saved.getLongitude(),
                "accuracy", saved.getAccuracy() != null ? saved.getAccuracy() : 0.0,
                "sharingStatus", saved.getSharingStatus(),
                "recordedAt", saved.getRecordedAt().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to start live GPS: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Update Live GPS Telemetry Beacon
     * POST /api/responder/location/update
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateLiveLocation(@RequestBody Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Authentication required."));
        }

        String responderEmail = auth.getName();
        Long taskId = getLongValue(req.get("taskId") != null ? req.get("taskId") : req.get("assignmentId"));
        Double lat = getDoubleValue(req.get("latitude"));
        Double lng = getDoubleValue(req.get("longitude"));
        Double accuracy = getDoubleValue(req.get("accuracy"));
        Double heading = getDoubleValue(req.get("heading"));
        Double speed = getDoubleValue(req.get("speed"));

        try {
            ResponderLocation saved = locationService.updateLocation(taskId, responderEmail, lat, lng, accuracy, heading, speed);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "GPS location updated successfully.",
                "locationId", saved.getId(),
                "responderId", saved.getResponderId() != null ? saved.getResponderId() : 0L,
                "taskId", saved.getAssignmentId(),
                "latitude", saved.getLatitude(),
                "longitude", saved.getLongitude(),
                "accuracy", saved.getAccuracy() != null ? saved.getAccuracy() : 0.0,
                "sharingStatus", saved.getSharingStatus(),
                "recordedAt", saved.getRecordedAt().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update live GPS: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Stop Live GPS Sharing
     * POST /api/responder/location/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<?> stopLiveLocation(@RequestBody(required = false) Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Authentication required."));
        }

        String responderEmail = auth.getName();
        Long taskId = null;
        if (req != null) {
            taskId = getLongValue(req.get("taskId") != null ? req.get("taskId") : req.get("assignmentId"));
        }

        try {
            ResponderLocation saved = locationService.stopLocationSharing(taskId, responderEmail);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Live location sharing is stopped.",
                "locationId", saved.getId(),
                "sharingStatus", "STOPPED",
                "recordedAt", saved.getRecordedAt().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to stop live GPS: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Get Current Logged-in Responder's Latest GPS Telemetry
     * GET /api/responder/location/me
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyLatestLocation() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Authentication required."));
        }

        Map<String, Object> result = locationService.getLatestLocation(auth.getName());
        return ResponseEntity.ok(result);
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

