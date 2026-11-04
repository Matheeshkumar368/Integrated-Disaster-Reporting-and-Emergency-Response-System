package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Alert;
import com.disaster.management.service.AdminPinService;
import com.disaster.management.service.AlertService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081"})
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    @Autowired
    private AlertService alertService;

    @Autowired
    private AdminPinService adminPinService;

    // Create a new Disaster Alert (Admin only)
    @PostMapping
    public ResponseEntity<?> createAlert(
            @RequestBody Alert alert,
            @RequestHeader(value = "X-Admin-Step-Up-Token", required = false) String stepUpToken,
            HttpServletRequest httpRequest) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminEmail = auth != null ? auth.getName() : "admin@disaster.gov.in";

        // If alert severity is "High" or alert is broadcasted, require step-up verification if PIN is enrolled
        if ("High".equalsIgnoreCase(alert.getSeverity())) {
            Map<String, Object> pinStatus = adminPinService.getPinStatus(adminEmail);
            if (Boolean.TRUE.equals(pinStatus.get("enrolled"))) {
                if (!adminPinService.validateStepUpAuthorization(stepUpToken, adminEmail)) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("status", "step_up_required");
                    resp.put("message", "High-severity disaster alerts require Admin Security PIN verification.");
                    return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(resp);
                }
            }
        }

        try {
            Alert savedAlert = alertService.createAlertAndDispatchTasks(alert, adminEmail);

            adminPinService.recordAudit(
                adminEmail,
                "CREATE_ALERT",
                "Alert",
                savedAlert.getAlertId().toString(),
                "SUCCESS",
                "Created " + savedAlert.getSeverity() + " alert in " + savedAlert.getRegion(),
                httpRequest.getRemoteAddr()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("alert", savedAlert);
            response.put("message", "Disaster alert created and responder rescue tasks dispatched.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create alert: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Failed to broadcast alert"));
        }
    }

    // Public / Citizen endpoint: get all alerts or active alerts
    @GetMapping
    public ResponseEntity<List<Alert>> getAllAlerts(
            @RequestParam(value = "status", required = false) String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(alertService.getActiveAlerts());
        }
        return ResponseEntity.ok(alertService.getAllAlerts());
    }

    // Public: get only broadcasted alerts
    @GetMapping("/broadcasted")
    public ResponseEntity<List<Alert>> getBroadcastedAlerts() {
        return ResponseEntity.ok(alertService.getBroadcastedAlerts());
    }

    // Admin broadcasts an existing alert to all citizens
    @PutMapping("/{alertId}/broadcast")
    public ResponseEntity<?> broadcastAlert(@PathVariable Long alertId) {
        try {
            Alert alert = alertService.getAlertById(alertId);
            if (alert != null) {
                alert.setBroadcasted(true);
                alert = alertService.updateAlert(alert);
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", "Alert not found"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Error broadcasting alert"));
        }
    }

    // Admin updates alert media URL
    @PutMapping("/{alertId}/media")
    public ResponseEntity<?> addMedia(
            @PathVariable Long alertId,
            @RequestBody Map<String, String> request) {

        try {
            Alert alert = alertService.getAlertById(alertId);
            if (alert != null) {
                alert.setMediaUrl(request.get("mediaUrl"));
                alert = alertService.updateAlert(alert);
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", "Alert not found"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Error attaching media"));
        }
    }

    // Filter alerts by region (Public)
    @GetMapping("/region/{region}")
    public ResponseEntity<List<Alert>> getAlertsByRegion(@PathVariable String region) {
        return ResponseEntity.ok(alertService.getAlertsByRegion(region));
    }

    // Responders or Admin acknowledge an alert (supports both PUT and POST)
    @PutMapping("/{alertId}/acknowledge")
    public ResponseEntity<?> acknowledgeAlert(@PathVariable Long alertId) {
        return executeAcknowledgeAlert(alertId);
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<?> acknowledgeAlertPost(@PathVariable Long alertId) {
        return executeAcknowledgeAlert(alertId);
    }

    private ResponseEntity<?> executeAcknowledgeAlert(Long alertId) {
        try {
            Alert alert = alertService.acknowledgeAlert(alertId);
            if (alert != null) {
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", "Alert not found"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Error acknowledging alert"));
        }
    }

    // Admin marks an alert as resolved (supports both PUT and POST)
    @PutMapping("/{alertId}/resolve")
    public ResponseEntity<?> resolveAlert(@PathVariable Long alertId) {
        return executeResolveAlert(alertId);
    }

    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<?> resolveAlertPost(@PathVariable Long alertId) {
        return executeResolveAlert(alertId);
    }

    private ResponseEntity<?> executeResolveAlert(Long alertId) {
        try {
            Alert alert = alertService.resolveAlert(alertId);
            if (alert != null) {
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", "Alert not found"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Error resolving alert"));
        }
    }

    // Admin deletes an alert
    @org.springframework.web.bind.annotation.DeleteMapping("/{alertId}")
    public ResponseEntity<?> deleteAlert(@PathVariable Long alertId) {
        try {
            Alert alert = alertService.getAlertById(alertId);
            if (alert == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Alert not found"));
            }
            alert.setStatus("Archived");
            alertService.updateAlert(alert);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Alert archived/removed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Error removing alert"));
        }
    }
}
