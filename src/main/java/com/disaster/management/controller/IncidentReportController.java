package com.disaster.management.controller;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.IncidentReport;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.service.IncidentReportService;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081"})
public class IncidentReportController {

    private static final Logger log = LoggerFactory.getLogger(IncidentReportController.class);

    @Autowired
    private IncidentReportService incidentReportService;

    @Autowired
    private UserRepository userRepository;

    // Create a new incident report (Responders & Admins)
    @PostMapping
    public ResponseEntity<Map<String, Object>> createReport(@RequestBody IncidentReport report) {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        String reporterName = userOpt.map(User::getName).orElse(email.split("@")[0]);

        try {
            report.setReportedBy(email);
            report.setReporterName(reporterName);
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
            log.error("Failed to create incident report: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to create report");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all reports (Admin gets all, Responder gets their own)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllReports() {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));

        List<IncidentReport> reports;
        if (isAdmin) {
            reports = incidentReportService.getAllReports();
        } else {
            reports = incidentReportService.getReportsByReporter(email);
        }

        response.put("status", "success");
        response.put("reports", reports);
        return ResponseEntity.ok(response);
    }

    // Get reports for the logged-in responder
    @GetMapping("/my-reports")
    public ResponseEntity<Map<String, Object>> getMyReports() {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        List<IncidentReport> reports = incidentReportService.getReportsByReporter(auth.getName());
        response.put("status", "success");
        response.put("reports", reports);
        return ResponseEntity.ok(response);
    }

    // Get report by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReportById(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

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
    public ResponseEntity<Map<String, Object>> updateReportStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();

        String status = request.get("status");
        if (status == null || status.trim().isEmpty()) {
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
            log.error("Failed to update status: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to update status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Delete report (Admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteReport(@PathVariable Long id) {
        Map<String, String> response = new HashMap<>();
        try {
            incidentReportService.deleteReport(id);
            response.put("status", "success");
            response.put("message", "Report deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to delete report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Export incident reports to CSV format for safety audit & academic reporting
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportReportsToCsv() {
        List<IncidentReport> reports = incidentReportService.getAllReports();
        StringBuilder sb = new StringBuilder();
        sb.append("Report ID,Title,Zone,Report Type,Status,Reported By,Reporter Name,Timestamp\n");

        for (IncidentReport r : reports) {
            sb.append(r.getReportId() != null ? r.getReportId() : 0).append(",");
            sb.append("\"").append(escapeCsv(r.getTitle())).append("\",");
            sb.append("\"").append(escapeCsv(r.getZone())).append("\",");
            sb.append("\"").append(escapeCsv(r.getReportType())).append("\",");
            sb.append("\"").append(escapeCsv(r.getStatus())).append("\",");
            sb.append("\"").append(escapeCsv(r.getReportedBy())).append("\",");
            sb.append("\"").append(escapeCsv(r.getReporterName())).append("\",");
            sb.append("\"").append(r.getTimestamp() != null ? r.getTimestamp().toString() : "").append("\"\n");
        }

        byte[] csvBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"disaster_incident_reports.csv\"")
            .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
            .body(csvBytes);
    }

    private String escapeCsv(String input) {
        if (input == null) return "";
        return input.replace("\"", "\"\"");
    }
}
