package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class CitizenReportService {

    private static final Logger log = LoggerFactory.getLogger(CitizenReportService.class);

    @Autowired
    private CitizenReportRepository reportRepository;

    @Autowired
    private RescueTaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminPinService adminPinService;

    @Autowired
    private IncidentTimelineService timelineService;

    @Transactional
    public CitizenReport createReport(CitizenReport report) {
        if (report.getMessage() == null || report.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Report message description is required");
        }
        if (report.getLocation() == null || report.getLocation().trim().isEmpty()) {
            throw new IllegalArgumentException("Report location is required");
        }
        report.setTimestamp(LocalDateTime.now());
        if (report.getStatus() == null || report.getStatus().trim().isEmpty()) {
            report.setStatus("Pending");
        }
        if (report.getProgressStage() == null || report.getProgressStage().trim().isEmpty()) {
            report.setProgressStage("Submitted");
        }
        if (report.getIncidentType() == null || report.getIncidentType().trim().isEmpty()) {
            report.setIncidentType("General");
        }
        if (report.getTitle() == null || report.getTitle().trim().isEmpty()) {
            report.setTitle(report.getIncidentType() + " at " + report.getLocation());
        }

        // Automatic fallback coordinate resolution if GPS coordinates are not explicitly passed
        if (report.getLatitude() == null || report.getLongitude() == null) {
            try {
                IndiaGeoGazetteer.GeoLocation geo = IndiaGeoGazetteer.resolveLocation(
                    report.getLocation(), report.getTitle(), null
                );
                if (geo != null) {
                    report.setLatitude(geo.latitude());
                    report.setLongitude(geo.longitude());
                    if (report.getAccuracy() == null) {
                        report.setAccuracy(100.0);
                    }
                }
            } catch (Exception e) {
                log.warn("Gazetteer resolution note: {}", e.getMessage());
            }
        }

        report.setIsDeleted(false);
        report.setIsArchived(false);
        CitizenReport saved = reportRepository.save(report);

        try {
            timelineService.logEvent(
                saved.getReportId(),
                "CITIZEN_REPORT",
                "REPORT_SUBMITTED",
                saved.getCitizenEmail(),
                "Citizen",
                saved.getCitizenEmail(),
                "Emergency distress report registered: " + saved.getTitle(),
                null,
                null
            );
        } catch (Exception e) {
            log.warn("Timeline event logging warning: {}", e.getMessage());
        }

        return saved;
    }

    public List<CitizenReport> getAllReports() {
        List<CitizenReport> reports = reportRepository.findByIsDeletedFalseOrderByTimestampDesc();
        reports.forEach(this::enrichReportWithLinkedTask);
        return reports;
    }

    public List<CitizenReport> getPendingReports() {
        List<CitizenReport> reports = reportRepository.findByStatusOrderByTimestampDesc("Pending");
        reports.forEach(this::enrichReportWithLinkedTask);
        return reports;
    }

    public List<CitizenReport> getReportsByCitizen(String email) {
        if (email == null || email.trim().isEmpty()) {
            return List.of();
        }
        List<CitizenReport> reports = reportRepository.findByCitizenEmailAndIsDeletedFalseOrderByTimestampDesc(email.trim().toLowerCase());
        reports.forEach(this::enrichReportWithLinkedTask);
        return reports;
    }

    public Optional<CitizenReport> getReportById(Long reportId) {
        Optional<CitizenReport> opt = reportRepository.findById(reportId);
        opt.ifPresent(this::enrichReportWithLinkedTask);
        return opt;
    }

    // Assign a responder directly to a Citizen Report (Admin operation)
    @Transactional
    public CitizenReport assignResponder(Long reportId, String responderEmail, String adminEmail) {
        if (reportId == null) {
            throw new IllegalArgumentException("Report ID is required");
        }
        if (responderEmail == null || responderEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Responder email is required");
        }

        CitizenReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Citizen Report not found with ID: " + reportId));

        if (Boolean.TRUE.equals(report.getIsDeleted())) {
            throw new IllegalStateException("Cannot assign responder to a deleted report");
        }

        User responder = userRepository.findByEmail(responderEmail.trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Responder not found with email: " + responderEmail));

        if (!"Responder".equalsIgnoreCase(responder.getRole())) {
            throw new IllegalArgumentException("Selected user is not registered as an emergency Responder");
        }

        String responderName = responder.getName() != null ? responder.getName() : responder.getEmail();
        String responderPhone = responder.getPhone() != null ? responder.getPhone() : "";

        // Update Citizen Report
        report.setAssignedResponderEmail(responder.getEmail());
        report.setAssignedResponderName(responderName);
        report.setAssignedResponderPhone(responderPhone);
        report.setStatus("Assigned");
        report.setProgressStage("Assigned");
        report.setAssignedAt(LocalDateTime.now());

        // Find or create the linked RescueTask
        List<RescueTask> existingTasks = taskRepository.findByCitizenReportId(reportId);
        RescueTask task;
        if (!existingTasks.isEmpty()) {
            task = existingTasks.get(0);
            task.setAssignedTo(responder.getEmail());
            task.setAssignedResponderName(responderName);
            task.setStatus("Assigned");
            task = taskRepository.save(task);
        } else {
            task = new RescueTask();
            task.setCitizenReportId(reportId);
            task.setTitle("Emergency Response - Report #" + reportId + " (" + (report.getLocation() != null ? report.getLocation() : "Field Zone") + ")");
            task.setDescription(report.getMessage());
            task.setZone(report.getLocation() != null ? report.getLocation() : (responder.getAssignedZone() != null ? responder.getAssignedZone() : "General"));
            task.setPriority(report.getSeverity() != null ? report.getSeverity() : "High");
            task.setStatus("Assigned");
            task.setAssignedTo(responder.getEmail());
            task.setAssignedResponderName(responderName);
            task.setCreatedBy(adminEmail != null ? adminEmail : "admin@disaster.gov.in");
            task.setLatitude(report.getLatitude());
            task.setLongitude(report.getLongitude());
            task.setLocationName(report.getLocation());
            task.setCreatedAt(LocalDateTime.now());
            task = taskRepository.save(task);
        }

        report.setRescueTaskId(task.getTaskId());

        // Update responder availability status
        if ("Available".equalsIgnoreCase(responder.getAvailabilityStatus()) || responder.getAvailabilityStatus() == null) {
            responder.setAvailabilityStatus("On Duty");
            userRepository.save(responder);
        }

        CitizenReport saved = reportRepository.save(report);

        // Timeline event
        try {
            timelineService.logEvent(
                reportId,
                "CITIZEN_REPORT",
                "RESPONDER_ASSIGNED",
                adminEmail != null ? adminEmail : "admin@disaster.gov.in",
                "Admin",
                responderName,
                "Assigned to " + responderName + " (" + responder.getEmail() + ")",
                null,
                null
            );
        } catch (Exception e) {
            log.warn("Timeline warning: {}", e.getMessage());
        }

        // Admin audit
        try {
            adminPinService.recordAudit(
                adminEmail != null ? adminEmail : "admin@disaster.gov.in",
                "ASSIGN_RESPONDER_TO_REPORT",
                "CitizenReport",
                String.valueOf(reportId),
                "SUCCESS",
                "Assigned " + responderName + " (" + responder.getEmail() + ") to Report #" + reportId,
                null
            );
        } catch (Exception e) {
            log.warn("Audit record warning: {}", e.getMessage());
        }

        return saved;
    }

    // Helper: Enriches CitizenReport with data from linked RescueTask if available
    private void enrichReportWithLinkedTask(CitizenReport r) {
        if (r == null) return;
        try {
            List<RescueTask> tasks = taskRepository.findByCitizenReportId(r.getReportId());
            if (!tasks.isEmpty()) {
                RescueTask t = tasks.get(0);
                if (r.getRescueTaskId() == null) {
                    r.setRescueTaskId(t.getTaskId());
                }
                if (r.getAssignedResponderEmail() == null && t.getAssignedTo() != null) {
                    r.setAssignedResponderEmail(t.getAssignedTo());
                }
                if (r.getAssignedResponderName() == null && t.getAssignedResponderName() != null) {
                    r.setAssignedResponderName(t.getAssignedResponderName());
                }
                if (r.getAssignedResponderPhone() == null && t.getAssignedTo() != null) {
                    userRepository.findByEmail(t.getAssignedTo()).ifPresent(u -> r.setAssignedResponderPhone(u.getPhone()));
                }
                // Reflect live mission status if report is behind
                if ("Pending".equalsIgnoreCase(r.getStatus()) && !"Pending".equalsIgnoreCase(t.getStatus())) {
                    r.setStatus(t.getStatus());
                }
            }
        } catch (Exception ignored) {}
    }

    // Paginated and filtered search
    public Map<String, Object> getReportsPaged(String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<CitizenReport> reportPage = reportRepository.searchReports(
            (search != null && !search.trim().isEmpty()) ? search.trim() : null,
            (status != null && !status.trim().isEmpty()) ? status.trim() : null,
            pageable
        );

        List<CitizenReport> content = reportPage.getContent();
        content.forEach(this::enrichReportWithLinkedTask);

        Map<String, Object> result = new HashMap<>();
        result.put("reports", content);
        result.put("currentPage", reportPage.getNumber());
        result.put("totalPages", reportPage.getTotalPages());
        result.put("totalElements", reportPage.getTotalElements());
        result.put("pageSize", reportPage.getSize());
        result.put("hasNext", reportPage.hasNext());
        result.put("hasPrevious", reportPage.hasPrevious());
        return result;
    }

    @Transactional
    public CitizenReport acknowledgeReport(Long reportId) {
        CitizenReport report = reportRepository.findById(reportId).orElse(null);
        if (report != null && !Boolean.TRUE.equals(report.getIsDeleted())) {
            report.setStatus("Acknowledged");
            report.setProgressStage("Accepted");
            report.setAcknowledgedAt(LocalDateTime.now());
            CitizenReport saved = reportRepository.save(report);

            // Synchronize linked task if present
            try {
                List<RescueTask> tasks = taskRepository.findByCitizenReportId(reportId);
                for (RescueTask t : tasks) {
                    if ("Pending".equalsIgnoreCase(t.getStatus()) || "Assigned".equalsIgnoreCase(t.getStatus())) {
                        t.setStatus("Acknowledged");
                        t.setAcknowledgedAt(LocalDateTime.now());
                        taskRepository.save(t);
                    }
                }
            } catch (Exception e) {
                log.warn("Task sync warning on acknowledge: {}", e.getMessage());
            }

            return saved;
        }
        return null;
    }

    @Transactional
    public CitizenReport resolveReport(Long reportId) {
        CitizenReport report = reportRepository.findById(reportId).orElse(null);
        if (report != null && !Boolean.TRUE.equals(report.getIsDeleted())) {
            report.setStatus("Resolved");
            report.setProgressStage("Resolved");
            report.setResolvedAt(LocalDateTime.now());
            CitizenReport saved = reportRepository.save(report);

            // Synchronize linked task if present
            try {
                List<RescueTask> tasks = taskRepository.findByCitizenReportId(reportId);
                for (RescueTask t : tasks) {
                    if (!"Completed".equalsIgnoreCase(t.getStatus()) && !"Resolved".equalsIgnoreCase(t.getStatus())) {
                        t.setStatus("Completed");
                        t.setCompletedAt(LocalDateTime.now());
                        taskRepository.save(t);
                    }
                }
            } catch (Exception e) {
                log.warn("Task sync warning on resolve: {}", e.getMessage());
            }

            return saved;
        }
        return null;
    }

    public long countPendingReports() {
        return reportRepository.countByStatus("Pending");
    }

    // Cancel / Invalidate report
    @Transactional
    public CitizenReport cancelReport(Long reportId, String reason, String actorEmail, boolean isAdmin) {
        CitizenReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));

        if (!isAdmin) {
            if (report.getCitizenEmail() == null || !report.getCitizenEmail().equalsIgnoreCase(actorEmail)) {
                throw new SecurityException("Unauthorized: You can only cancel your own report");
            }
            if (!"Pending".equalsIgnoreCase(report.getStatus())) {
                throw new IllegalStateException("Cannot cancel report: Response unit already assigned or in progress");
            }
        }

        report.setStatus("Cancelled");
        CitizenReport saved = reportRepository.save(report);

        // Record audit trail if administrative cancellation
        if (isAdmin) {
            adminPinService.recordAudit(
                actorEmail,
                "CANCEL_CITIZEN_REPORT",
                "CitizenReport",
                String.valueOf(reportId),
                "SUCCESS",
                reason != null && !reason.trim().isEmpty() ? reason.trim() : "Cancelled by Admin as invalid or duplicate",
                null
            );
        }

        timelineService.logEvent(
            reportId,
            "CITIZEN_REPORT",
            "REPORT_CANCELLED",
            actorEmail,
            isAdmin ? "Admin" : "Citizen",
            actorEmail,
            "Incident report cancelled: " + (reason != null ? reason : "No reason specified"),
            null,
            "{\"reason\":\"" + (reason != null ? reason.replace("\"", "\\\"") : "") + "\"}"
        );

        return saved;
    }

    // Archive report
    @Transactional
    public CitizenReport archiveReport(Long reportId, String actorEmail) {
        CitizenReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));

        report.setIsArchived(true);
        CitizenReport saved = reportRepository.save(report);

        adminPinService.recordAudit(
            actorEmail,
            "ARCHIVE_CITIZEN_REPORT",
            "CitizenReport",
            String.valueOf(reportId),
            "SUCCESS",
            "Report #" + reportId + " archived",
            null
        );

        return saved;
    }

    // Delete report with database integrity verification
    @Transactional
    public Map<String, Object> deleteReport(Long reportId, String reason, String actorEmail, boolean forcePermanent) {
        CitizenReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));

        // 1. Data Integrity Check: Are there active tasks linked to this report?
        long activeTaskCount = taskRepository.countByCitizenReportIdAndStatusNotIn(
            reportId,
            List.of("Completed", "Resolved", "Cancelled")
        );

        if (activeTaskCount > 0) {
            throw new IllegalStateException(
                "Integrity Constraint: Cannot delete Report #" + reportId + 
                " because " + activeTaskCount + " active response mission(s) are currently deployed for it. " +
                "Complete or cancel active tasks before deleting, or use soft-archival."
            );
        }

        Map<String, Object> result = new HashMap<>();

        if (forcePermanent) {
            // Permanent deletion (audited)
            reportRepository.delete(report);
            adminPinService.recordAudit(
                actorEmail,
                "PERMANENT_DELETE_REPORT",
                "CitizenReport",
                String.valueOf(reportId),
                "SUCCESS",
                "Permanently purged Report #" + reportId + ". Reason: " + (reason != null ? reason : "Administrative purge"),
                null
            );
            result.put("mode", "PERMANENT");
            result.put("message", "Report #" + reportId + " has been permanently deleted from database.");
        } else {
            // Soft deletion / Archival removal
            report.setIsDeleted(true);
            report.setDeletionReason(reason != null ? reason.trim() : "Deleted by administrator");
            report.setDeletedAt(LocalDateTime.now());
            report.setDeletedBy(actorEmail);
            reportRepository.save(report);

            adminPinService.recordAudit(
                actorEmail,
                "DELETE_CITIZEN_REPORT",
                "CitizenReport",
                String.valueOf(reportId),
                "SUCCESS",
                "Soft-deleted Report #" + reportId + ". Reason: " + (reason != null ? reason : "Admin cleanup"),
                null
            );
            result.put("mode", "SOFT_DELETE");
            result.put("message", "Report #" + reportId + " has been safely removed and archived.");
        }

        result.put("status", "success");
        result.put("reportId", reportId);
        return result;
    }

    @Transactional
    public CitizenReport startCitizenLiveTracking(Long reportId, String citizenEmail, Double lat, Double lng, Double accuracy) {
        CitizenReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));
        if (!report.getCitizenEmail().equalsIgnoreCase(citizenEmail)) {
            throw new SecurityException("Unauthorized: You can only share location for your own report.");
        }
        report.setLiveTrackingEnabled(true);
        report.setLiveLatitude(lat);
        report.setLiveLongitude(lng);
        report.setLiveAccuracy(accuracy);
        report.setLiveLastUpdatedAt(LocalDateTime.now());
        CitizenReport saved = reportRepository.save(report);

        timelineService.logEvent(
            reportId,
            "CITIZEN_REPORT",
            "LIVE_LOCATION_STARTED",
            citizenEmail,
            "Citizen",
            citizenEmail,
            "Citizen enabled live location sharing.",
            null,
            "{\"lat\":" + lat + ",\"lng\":" + lng + ",\"acc\":" + accuracy + "}"
        );

        return saved;
    }

    @Transactional
    public CitizenReport updateCitizenLiveLocation(Long reportId, String citizenEmail, Double lat, Double lng, Double accuracy) {
        CitizenReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));
        if (!report.getCitizenEmail().equalsIgnoreCase(citizenEmail)) {
            throw new SecurityException("Unauthorized: You can only share location for your own report.");
        }
        report.setLiveTrackingEnabled(true);
        report.setLiveLatitude(lat);
        report.setLiveLongitude(lng);
        report.setLiveAccuracy(accuracy);
        report.setLiveLastUpdatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    @Transactional
    public CitizenReport stopCitizenLiveTracking(Long reportId, String citizenEmail) {
        CitizenReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));
        if (!report.getCitizenEmail().equalsIgnoreCase(citizenEmail)) {
            throw new SecurityException("Unauthorized: You can only update your own report.");
        }
        report.setLiveTrackingEnabled(false);
        CitizenReport saved = reportRepository.save(report);

        timelineService.logEvent(
            reportId,
            "CITIZEN_REPORT",
            "LIVE_LOCATION_STOPPED",
            citizenEmail,
            "Citizen",
            citizenEmail,
            "Citizen stopped live location sharing.",
            null,
            null
        );

        return saved;
    }

    public List<CitizenReport> getActiveLiveReports() {
        return reportRepository.findByLiveTrackingEnabledTrueAndIsDeletedFalse();
    }
}
