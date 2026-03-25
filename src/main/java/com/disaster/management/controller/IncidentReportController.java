package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.IncidentReport;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtUtil;
import com.disaster.management.service.IncidentReportService;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081"})
public class IncidentReportController {

    @Autowired
    private IncidentReportService incidentReportService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;

    // Create a new incident report (Responder only)
    @PostMapping
    public ResponseEntity<Map<String, Object>> createReport(@RequestBody IncidentReport report, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        // Validate token and get email
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String email = jwtUtil.extractEmail(token);
        
        // Verify responder role
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent() || !"Responder".equals(userOpt.get().getRole())) {
            response.put("status", "error");
            response.put("message", "Access denied. Responder only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        try {
            report.setReportedBy(email);
            report.setReporterName(userOpt.get().getName());
            IncidentReport createdReport = incidentReportService.createReport(report);
            
            response.put("status", "success");
            response.put("message", "Report submitted successfully");
            response.put("report", createdReport);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to create report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all reports (Admin)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllReports(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);
        
        List<IncidentReport> reports;
        if ("Admin".equals(role)) {
            reports = incidentReportService.getAllReports();
        } else {
            // Responders only see their own reports
            reports = incidentReportService.getReportsByReporter(email);
        }
        
        response.put("status", "success");
        response.put("reports", reports);
        return ResponseEntity.ok(response);
    }

    // Get reports for logged-in responder
    @GetMapping("/my-reports")
    public ResponseEntity<Map<String, Object>> getMyReports(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String email = jwtUtil.extractEmail(token);
        List<IncidentReport> reports = incidentReportService.getReportsByReporter(email);
        
        response.put("status", "success");
        response.put("reports", reports);
        return ResponseEntity.ok(response);
    }

    // Get report by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReportById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        Optional<IncidentReport> report = incidentReportService.getReportById(id);
        if (report.isPresent()) {
            response.put("status", "success");
            response.put("report", report.get());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Report not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // Update report status (Admin only)
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateReportStatus(@PathVariable Long id, @RequestBody Map<String, String> request, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String role = jwtUtil.extractRole(token);
        if (!"Admin".equals(role)) {
            response.put("status", "error");
            response.put("message", "Access denied. Admin only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        String status = request.get("status");
        if (status == null || status.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Status is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            IncidentReport report = incidentReportService.updateReportStatus(id, status);
            
            if (report != null) {
                response.put("status", "success");
                response.put("message", "Report status updated");
                response.put("report", report);
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Report not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to update report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Delete report (Admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteReport(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String role = jwtUtil.extractRole(token);
        if (!"Admin".equals(role)) {
            response.put("status", "error");
            response.put("message", "Access denied. Admin only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        try {
            incidentReportService.deleteReport(id);
            response.put("status", "success");
            response.put("message", "Report deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to delete report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get reports by zone
    @GetMapping("/zone/{zone}")
    public ResponseEntity<Map<String, Object>> getReportsByZone(@PathVariable String zone, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        List<IncidentReport> reports = incidentReportService.getReportsByZone(zone);
        
        response.put("status", "success");
        response.put("reports", reports);
        return ResponseEntity.ok(response);
    }
}
