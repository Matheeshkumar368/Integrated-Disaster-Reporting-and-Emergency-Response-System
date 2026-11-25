package com.disaster.management.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.OfficialAlertRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.TrackingSessionRepository;
import com.disaster.management.service.AdminPinService;

@RestController
@RequestMapping("/api/admin/data")
@CrossOrigin(origins = {"*"})
public class AdminDataHygieneController {

    private static final Logger log = LoggerFactory.getLogger(AdminDataHygieneController.class);

    @Autowired
    private CitizenReportRepository reportRepository;

    @Autowired
    private RescueTaskRepository taskRepository;

    @Autowired
    private OfficialAlertRepository alertRepository;

    @Autowired
    private TrackingSessionRepository trackingRepository;

    @Autowired
    private AdminPinService adminPinService;

    @GetMapping("/hygiene-stats")
    @PreAuthorize("hasRole('Admin') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getHygieneStats() {
        Map<String, Object> stats = new HashMap<>();
        List<CitizenReport> allReports = reportRepository.findAll();
        long pending = 0;
        long acknowledged = 0;
        long resolved = 0;
        long archived = 0;
        long deleted = 0;

        Map<String, List<Long>> duplicateSignatures = new HashMap<>();

        for (CitizenReport r : allReports) {
            if (Boolean.TRUE.equals(r.getIsDeleted())) {
                deleted++;
                continue;
            }
            if (Boolean.TRUE.equals(r.getIsArchived())) {
                archived++;
            }
            String st = r.getStatus() != null ? r.getStatus().toLowerCase() : "pending";
            if ("pending".equals(st)) pending++;
            else if ("acknowledged".equals(st) || "assigned".equals(st)) acknowledged++;
            else if ("resolved".equals(st) || "completed".equals(st)) resolved++;

            // Create signature for duplicate detection
            String sig = (r.getCitizenEmail() != null ? r.getCitizenEmail().trim().toLowerCase() : "") + "|" +
                         (r.getMessage() != null ? r.getMessage().trim().toLowerCase() : "");
            duplicateSignatures.computeIfAbsent(sig, k -> new ArrayList<>()).add(r.getReportId());
        }

        int duplicateCount = 0;
        for (List<Long> ids : duplicateSignatures.values()) {
            if (ids.size() > 1) {
                duplicateCount += (ids.size() - 1);
            }
        }

        stats.put("totalReports", allReports.size());
        stats.put("pendingReports", pending);
        stats.put("acknowledgedReports", acknowledged);
        stats.put("resolvedReports", resolved);
        stats.put("archivedReports", archived);
        stats.put("deletedReports", deleted);
        stats.put("duplicateReportsDetected", duplicateCount);
        stats.put("totalRescueTasks", taskRepository.count());
        stats.put("officialAlerts", alertRepository.count());
        stats.put("activeTrackingSessions", trackingRepository.findByStatus("ACTIVE").size());

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/clean-duplicates")
    @PreAuthorize("hasRole('Admin') or hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> cleanDuplicateReports() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth != null ? auth.getName() : "admin@disaster.gov.in";

        List<CitizenReport> allReports = reportRepository.findByIsDeletedFalseOrderByTimestampDesc();
        Map<String, List<CitizenReport>> signatureMap = new HashMap<>();

        for (CitizenReport r : allReports) {
            String sig = (r.getCitizenEmail() != null ? r.getCitizenEmail().trim().toLowerCase() : "") + "|" +
                         (r.getMessage() != null ? r.getMessage().trim().toLowerCase() : "");
            signatureMap.computeIfAbsent(sig, k -> new ArrayList<>()).add(r);
        }

        int cleanedCount = 0;
        List<Long> cleanedIds = new ArrayList<>();

        for (List<CitizenReport> list : signatureMap.values()) {
            if (list.size() > 1) {
                // Keep the first (oldest or primary), mark others as archived/deleted
                for (int i = 1; i < list.size(); i++) {
                    CitizenReport duplicate = list.get(i);
                    duplicate.setIsArchived(true);
                    duplicate.setIsDeleted(true);
                    duplicate.setDeletionReason("Automated duplicate cleanup: identical submission to Report #" + list.get(0).getReportId());
                    duplicate.setDeletedAt(LocalDateTime.now());
                    duplicate.setDeletedBy(actor);
                    reportRepository.save(duplicate);
                    cleanedIds.add(duplicate.getReportId());
                    cleanedCount++;
                }
            }
        }

        if (cleanedCount > 0) {
            adminPinService.recordAudit(
                actor,
                "CLEAN_DUPLICATE_REPORTS",
                "CitizenReport",
                cleanedIds.toString(),
                "SUCCESS",
                "Archived and cleaned " + cleanedCount + " duplicate demonstration submissions.",
                null
            );
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "success");
        resp.put("duplicatesCleaned", cleanedCount);
        resp.put("cleanedReportIds", cleanedIds);
        resp.put("message", "Hygiene cycle complete. Cleaned " + cleanedCount + " duplicate submissions.");
        return ResponseEntity.ok(resp);
    }
}

