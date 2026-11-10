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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.service.CitizenReportService;

@RestController
@RequestMapping("/api/citizen-reports")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081"})
public class CitizenReportController {

    private static final Logger log = LoggerFactory.getLogger(CitizenReportController.class);

    @Autowired
    private CitizenReportService reportService;

    // Citizens submit an incident report
    @PostMapping
    public ResponseEntity<?> createReport(@RequestBody CitizenReport report) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Authentication required"));
        }

        try {
            String citizenEmail = auth.getName();
            report.setCitizenEmail(citizenEmail);

            CitizenReport savedReport = reportService.createReport(report);
            log.info("Citizen report created successfully by: {}", citizenEmail);
            return ResponseEntity.ok(savedReport);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create citizen report: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Failed to save emergency report"));
        }
    }

    // Citizen retrieves only their own submitted reports
    @GetMapping("/my-reports")
    public ResponseEntity<?> getMyReports() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Authentication required"));
        }

        List<CitizenReport> myReports = reportService.getReportsByCitizen(auth.getName());
        return ResponseEntity.ok(myReports);
    }

    // Fetch single report by ID with strict ownership verification
    @GetMapping("/{reportId}")
    public ResponseEntity<?> getReportById(@PathVariable Long reportId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Authentication required"));
        }

        java.util.Optional<CitizenReport> reportOpt = reportService.getReportById(reportId);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Report not found with id: " + reportId));
        }

        CitizenReport report = reportOpt.get();
        String callerEmail = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        boolean isResponder = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Responder"));

        if (!isAdmin && !isResponder) {
            if (report.getCitizenEmail() == null || !report.getCitizenEmail().trim().equalsIgnoreCase(callerEmail.trim())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "error", "message", "Forbidden: You cannot access reports belonging to another citizen."));
            }
        }

        return ResponseEntity.ok(report);
    }

    // Responders and Admins: view all citizen reports
    @GetMapping
    public ResponseEntity<List<CitizenReport>> getAllReports() {
        return ResponseEntity.ok(reportService.getAllReports());
    }

    // Paginated and filtered search
    @GetMapping("/paged")
    public ResponseEntity<?> getReportsPaged(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(reportService.getReportsPaged(search, status, page, size));
    }

    // Responders and Admins: view pending rescue requests
    @GetMapping("/pending")
    public ResponseEntity<List<CitizenReport>> getPendingReports() {
        return ResponseEntity.ok(reportService.getPendingReports());
    }

    // Admin assigns a Responder directly to a Citizen Report
    @PutMapping("/{reportId}/assign")
    public ResponseEntity<?> assignReport(
            @PathVariable Long reportId,
            @RequestParam(required = false) String responderEmail,
            @RequestBody(required = false) Map<String, String> body) {
        return executeAssignReport(reportId, responderEmail, body);
    }

    @PostMapping("/{reportId}/assign")
    public ResponseEntity<?> assignReportPost(
            @PathVariable Long reportId,
            @RequestParam(required = false) String responderEmail,
            @RequestBody(required = false) Map<String, String> body) {
        return executeAssignReport(reportId, responderEmail, body);
    }

    private ResponseEntity<?> executeAssignReport(Long reportId, String responderEmail, Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Access denied: Admin role required to assign responders"));
        }

        String email = responderEmail;
        if ((email == null || email.trim().isEmpty()) && body != null) {
            email = body.get("responderEmail") != null ? body.get("responderEmail") : body.get("email");
        }

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Responder email is required"));
        }

        try {
            CitizenReport updated = reportService.assignResponder(reportId, email.trim(), auth.getName());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Responder assigned successfully to incident #" + reportId,
                "report", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "conflict", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to assign responder to report {}: {}", reportId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Internal error assigning responder: " + e.getMessage()));
        }
    }

    // Responders or Admin acknowledges report
    @PutMapping("/{reportId}/acknowledge")
    public ResponseEntity<?> acknowledgeReport(@PathVariable Long reportId) {
        try {
            CitizenReport report = reportService.acknowledgeReport(reportId);
            if (report != null) {
                return ResponseEntity.ok(report);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", "Report not found with id: " + reportId));
        } catch (Exception e) {
            log.error("Error acknowledging report {}: {}", reportId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", "Error acknowledging report"));
        }
    }

    // Responders or Admin marks report as resolved
    @PutMapping("/{reportId}/resolve")
    public ResponseEntity<?> resolveReport(@PathVariable Long reportId) {
        try {
            CitizenReport report = reportService.resolveReport(reportId);
            if (report != null) {
                return ResponseEntity.ok(report);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", "Report not found with id: " + reportId));
        } catch (Exception e) {
            log.error("Error resolving report {}: {}", reportId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", "Error resolving report"));
        }
    }

    // Admin archives a report
    @PutMapping("/{reportId}/archive")
    public ResponseEntity<?> archiveReport(@PathVariable Long reportId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Access denied: Admin role required to archive reports"));
        }

        try {
            CitizenReport report = reportService.archiveReport(reportId, auth.getName());
            return ResponseEntity.ok(Map.of("status", "success", "message", "Report archived", "report", report));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // Delete or Cancel report
    // If Admin calls with ?action=delete or ?permanent=true -> executes report deletion with integrity check
    // If Citizen or ?action=cancel -> marks report as cancelled
    @DeleteMapping("/{reportId}")
    public ResponseEntity<?> deleteOrCancelReport(
            @PathVariable Long reportId,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Authentication required"));
        }

        String callerEmail = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));

        // If explicitly requested delete or permanent, or action is delete
        boolean isDeleteAction = "delete".equalsIgnoreCase(action) || permanent;

        if (isDeleteAction) {
            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "error", "message", "Access denied: Only Administrators can delete incident records."));
            }

            try {
                Map<String, Object> delResult = reportService.deleteReport(reportId, reason, callerEmail, permanent);
                return ResponseEntity.ok(delResult);
            } catch (IllegalStateException e) {
                // Active task integrity constraint
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "conflict", "message", e.getMessage()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
            } catch (Exception e) {
                log.error("Failed to delete report {}: {}", reportId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Failed to delete report: " + e.getMessage()));
            }
        }

        // Standard Cancellation
        try {
            CitizenReport report = reportService.cancelReport(reportId, reason, callerEmail, isAdmin);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Report #" + reportId + " has been successfully invalidated/cancelled.",
                "report", report
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to cancel report {}: {}", reportId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Failed to cancel report: " + e.getMessage()));
        }
    }

    // Citizen or Admin cancels report via PUT
    @PutMapping("/{reportId}/cancel")
    public ResponseEntity<?> cancelReportPut(
            @PathVariable Long reportId,
            @RequestBody(required = false) Map<String, Object> req) {
        String reason = req != null && req.get("reason") != null ? req.get("reason").toString() : null;
        return deleteOrCancelReport(reportId, reason, "cancel", false);
    }

    // Citizen starts live location broadcast during an emergency
    @PostMapping("/{reportId}/live-location/start")
    public ResponseEntity<?> startLiveLocation(@PathVariable Long reportId, @RequestBody Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Authentication required"));
        }
        Double lat = req.get("latitude") != null ? Double.valueOf(req.get("latitude").toString()) : null;
        Double lng = req.get("longitude") != null ? Double.valueOf(req.get("longitude").toString()) : null;
        Double accuracy = req.get("accuracy") != null ? Double.valueOf(req.get("accuracy").toString()) : null;
        try {
            CitizenReport updated = reportService.startCitizenLiveTracking(reportId, auth.getName(), lat, lng, accuracy);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Live location sharing activated", "report", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // Citizen transmits updated location beacon
    @PostMapping("/{reportId}/live-location/update")
    public ResponseEntity<?> updateLiveLocation(@PathVariable Long reportId, @RequestBody Map<String, Object> req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Authentication required"));
        }
        Double lat = req.get("latitude") != null ? Double.valueOf(req.get("latitude").toString()) : null;
        Double lng = req.get("longitude") != null ? Double.valueOf(req.get("longitude").toString()) : null;
        Double accuracy = req.get("accuracy") != null ? Double.valueOf(req.get("accuracy").toString()) : null;
        try {
            CitizenReport updated = reportService.updateCitizenLiveLocation(reportId, auth.getName(), lat, lng, accuracy);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Live location updated", "report", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // Citizen stops live location broadcast
    @PostMapping("/{reportId}/live-location/stop")
    public ResponseEntity<?> stopLiveLocation(@PathVariable Long reportId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Authentication required"));
        }
        try {
            CitizenReport updated = reportService.stopCitizenLiveTracking(reportId, auth.getName());
            return ResponseEntity.ok(Map.of("status", "success", "message", "Live location sharing terminated", "report", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // Live reports for Admin and Responder radar maps
    @GetMapping("/active-live")
    public ResponseEntity<?> getActiveLiveReports() {
        return ResponseEntity.ok(reportService.getActiveLiveReports());
    }
}
