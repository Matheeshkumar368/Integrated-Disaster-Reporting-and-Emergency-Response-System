package com.disaster.management.controller;

import java.util.List;

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

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.security.JwtUtil;
import com.disaster.management.service.CitizenReportService;

@RestController
@RequestMapping("/api/citizen-reports")
public class CitizenReportController {

    @Autowired
    private CitizenReportService reportService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> createReport(
            @RequestBody CitizenReport report,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);

            if (!"Citizen".equals(role)) {
                return ResponseEntity.status(403).body("Only Citizens can create reports");
            }

            report.setCitizenEmail(email);
            CitizenReport savedReport = reportService.createReport(report);
            return ResponseEntity.ok(savedReport);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating report: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<CitizenReport>> getAllReports(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Allow responders and admins to view reports
        return ResponseEntity.ok(reportService.getAllReports());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<CitizenReport>> getPendingReports() {
        return ResponseEntity.ok(reportService.getPendingReports());
    }

    @PutMapping("/{reportId}/acknowledge")
    public ResponseEntity<?> acknowledgeReport(
            @PathVariable Long reportId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String role = jwtUtil.extractRole(token);

            if (!"Responder".equals(role) && !"Admin".equals(role)) {
                return ResponseEntity.status(403).body("Only Responders or Admins can acknowledge reports");
            }

            CitizenReport report = reportService.acknowledgeReport(reportId);
            if (report != null) {
                return ResponseEntity.ok(report);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error acknowledging report: " + e.getMessage());
        }
    }

    @PutMapping("/{reportId}/resolve")
    public ResponseEntity<?> resolveReport(
            @PathVariable Long reportId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String role = jwtUtil.extractRole(token);

            if (!"Responder".equals(role) && !"Admin".equals(role)) {
                return ResponseEntity.status(403).body("Only Responders or Admins can resolve reports");
            }

            CitizenReport report = reportService.resolveReport(reportId);
            if (report != null) {
                return ResponseEntity.ok(report);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error resolving report: " + e.getMessage());
        }
    }
}
