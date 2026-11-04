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
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.RescueTaskRepository;

@Service
public class CitizenReportService {

    private static final Logger log = LoggerFactory.getLogger(CitizenReportService.class);

    @Autowired
    private CitizenReportRepository reportRepository;

    @Autowired
    private RescueTaskRepository taskRepository;

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
        if (report.getIncidentType() == null || report.getIncidentType().trim().isEmpty()) {
            report.setIncidentType("General");
        }
        report.setIsDeleted(false);
        report.setIsArchived(false);
        return reportRepository.save(report);
    }

    public List<CitizenReport> getAllReports() {
        return reportRepository.findByIsDeletedFalseOrderByTimestampDesc();
    }

    public List<CitizenReport> getPendingReports() {
        return reportRepository.findByStatusOrderByTimestampDesc("Pending");
    }

    public List<CitizenReport> getReportsByCitizen(String email) {
        if (email == null || email.trim().isEmpty()) {
            return List.of();
        }
        return reportRepository.findByCitizenEmailAndIsDeletedFalseOrderByTimestampDesc(email.trim().toLowerCase());
    }

    public Optional<CitizenReport> getReportById(Long reportId) {
        return reportRepository.findById(reportId);
    }

    // Paginated and filtered search
    public Map<String, Object> getReportsPaged(String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<CitizenReport> reportPage = reportRepository.searchReports(
            (search != null && !search.trim().isEmpty()) ? search.trim() : null,
            (status != null && !status.trim().isEmpty()) ? status.trim() : null,
            pageable
        );

        Map<String, Object> result = new HashMap<>();
        result.put("reports", reportPage.getContent());
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
            return reportRepository.save(report);
        }
        return null;
    }

    @Transactional
    public CitizenReport resolveReport(Long reportId) {
        CitizenReport report = reportRepository.findById(reportId).orElse(null);
        if (report != null && !Boolean.TRUE.equals(report.getIsDeleted())) {
            report.setStatus("Resolved");
            return reportRepository.save(report);
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
