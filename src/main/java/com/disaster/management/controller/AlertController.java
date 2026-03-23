package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Alert;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtUtil;
import com.disaster.management.service.AlertService;
import com.disaster.management.service.RescueTaskService;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertService alertService;
    
    @Autowired
    private RescueTaskService rescueTaskService;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> createAlert(
            @RequestBody Alert alert,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);

            if (!"Admin".equals(role)) {
                return ResponseEntity.status(403).body("Only Admin can create alerts");
            }

            alert.setCreatedBy(email);
            Alert savedAlert = alertService.createAlert(alert);
            
            // Automatically create tasks for responders in the matching zone
            if (alert.getRegion() != null && !alert.getRegion().isEmpty()) {
                List<User> responders = userRepository.findByRoleAndAssignedZone("Responder", alert.getRegion());
                
                for (User responder : responders) {
                    RescueTask task = new RescueTask();
                    task.setTitle("Alert Response: " + alert.getTitle());
                    task.setDescription("Respond to alert in " + alert.getRegion() + ". " + alert.getMessage());
                    task.setZone(alert.getRegion());
                    task.setPriority(alert.getSeverity() != null ? alert.getSeverity() : "Medium");
                    task.setCreatedBy(email);
                    task.setAssignedTo(responder.getEmail());
                    task.setAssignedResponderName(responder.getName());
                    task.setLatitude(alert.getLatitude());
                    task.setLongitude(alert.getLongitude());
                    task.setLocationName(alert.getRegion());
                    task.setStatus("Pending");
                    rescueTaskService.createTask(task);
                }
                
                // Also create unassigned tasks for general responders (no specific zone)
                List<User> allResponders = userRepository.findByRole("Responder");
                for (User responder : allResponders) {
                    if (responder.getAssignedZone() == null || responder.getAssignedZone().isEmpty()) {
                        RescueTask task = new RescueTask();
                        task.setTitle("Alert Response: " + alert.getTitle());
                        task.setDescription("Respond to alert in " + alert.getRegion() + ". " + alert.getMessage());
                        task.setZone(alert.getRegion());
                        task.setPriority(alert.getSeverity() != null ? alert.getSeverity() : "Medium");
                        task.setCreatedBy(email);
                        task.setAssignedTo(responder.getEmail());
                        task.setAssignedResponderName(responder.getName());
                        task.setLatitude(alert.getLatitude());
                        task.setLongitude(alert.getLongitude());
                        task.setLocationName(alert.getRegion());
                        task.setStatus("Pending");
                        rescueTaskService.createTask(task);
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("alert", savedAlert);
            response.put("message", "Alert created and tasks assigned to responders");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating alert: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Alert>> getAllAlerts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Allow public access to view alerts - no authentication required
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    // Get only broadcasted alerts for citizens
    @GetMapping("/broadcasted")
    public ResponseEntity<List<Alert>> getBroadcastedAlerts() {
        return ResponseEntity.ok(alertService.getBroadcastedAlerts());
    }

    // Broadcast an alert to citizens
    @PutMapping("/{alertId}/broadcast")
    public ResponseEntity<?> broadcastAlert(
            @PathVariable Long alertId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String role = jwtUtil.extractRole(token);

            if (!"Admin".equals(role)) {
                return ResponseEntity.status(403).body("Only Admin can broadcast alerts");
            }

            Alert alert = alertService.getAlertById(alertId);
            if (alert != null) {
                alert.setBroadcasted(true);
                alert = alertService.updateAlert(alert);
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error broadcasting alert: " + e.getMessage());
        }
    }

    // Update alert with media URL
    @PutMapping("/{alertId}/media")
    public ResponseEntity<?> addMedia(
            @PathVariable Long alertId,
            @RequestBody java.util.Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String role = jwtUtil.extractRole(token);

            if (!"Admin".equals(role)) {
                return ResponseEntity.status(403).body("Only Admin can add media to alerts");
            }

            Alert alert = alertService.getAlertById(alertId);
            if (alert != null) {
                alert.setMediaUrl(request.get("mediaUrl"));
                alert = alertService.updateAlert(alert);
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error adding media: " + e.getMessage());
        }
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<List<Alert>> getAlertsByRegion(@PathVariable String region) {
        return ResponseEntity.ok(alertService.getAlertsByRegion(region));
    }

    @PutMapping("/{alertId}/acknowledge")
    public ResponseEntity<?> acknowledgeAlert(
            @PathVariable Long alertId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String role = jwtUtil.extractRole(token);

            if (!"Responder".equals(role) && !"Admin".equals(role)) {
                return ResponseEntity.status(403).body("Only Responder or Admin can acknowledge alerts");
            }

            Alert alert = alertService.acknowledgeAlert(alertId);
            if (alert != null) {
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error acknowledging alert: " + e.getMessage());
        }
    }

    @PutMapping("/{alertId}/resolve")
    public ResponseEntity<?> resolveAlert(
            @PathVariable Long alertId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String role = jwtUtil.extractRole(token);

            if (!"Admin".equals(role)) {
                return ResponseEntity.status(403).body("Only Admin can resolve alerts");
            }

            Alert alert = alertService.resolveAlert(alertId);
            if (alert != null) {
                return ResponseEntity.ok(alert);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error resolving alert: " + e.getMessage());
        }
    }
}
