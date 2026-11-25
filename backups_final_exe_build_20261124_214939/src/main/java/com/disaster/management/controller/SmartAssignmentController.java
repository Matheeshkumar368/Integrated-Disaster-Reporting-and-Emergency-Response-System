package com.disaster.management.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.service.SmartAssignmentService;

@RestController
@RequestMapping("/api/admin/assignments")
@CrossOrigin(origins = {"*"})
public class SmartAssignmentController {

    @Autowired
    private SmartAssignmentService smartAssignmentService;

    @GetMapping("/recommendations/{taskId}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> getRecommendationsForTask(@PathVariable Long taskId) {
        try {
            List<Map<String, Object>> recommendations = smartAssignmentService.getRecommendationsForTask(taskId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "taskId", taskId,
                "recommendations", recommendations
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> evaluateCustomLocation(@RequestBody Map<String, Object> req) {
        Double targetLat = req.get("latitude") != null ? Double.parseDouble(req.get("latitude").toString()) : null;
        Double targetLng = req.get("longitude") != null ? Double.parseDouble(req.get("longitude").toString()) : null;
        String zone = (String) req.get("zone");
        String priority = (String) req.get("priority");
        String description = (String) req.get("description");

        List<Map<String, Object>> recommendations = smartAssignmentService.evaluateResponders(
            targetLat, targetLng, zone, priority, description
        );

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "recommendations", recommendations
        ));
    }
}

