package com.disaster.management.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.OfficialAlert;
import com.disaster.management.repository.OfficialAlertRepository;
import com.disaster.management.service.ImdWeatherService;
import com.disaster.management.service.NdmaSyncService;

/**
 * Controller for Real-Time Official Disaster Warnings and Observations.
 * Connects dashboards directly to verified NDMA SACHET and IMD observations.
 */
@RestController
@RequestMapping("/api")
public class OfficialAlertController {

    private final OfficialAlertRepository officialAlertRepository;
    private final NdmaSyncService ndmaSyncService;
    private final ImdWeatherService imdWeatherService;

    public OfficialAlertController(OfficialAlertRepository officialAlertRepository,
                                  NdmaSyncService ndmaSyncService,
                                  ImdWeatherService imdWeatherService) {
        this.officialAlertRepository = officialAlertRepository;
        this.ndmaSyncService = ndmaSyncService;
        this.imdWeatherService = imdWeatherService;
    }

    /**
     * Retrieves all currently active (non-expired) official government warnings.
     * Publicly accessible for disaster awareness and map visualization.
     */
    @GetMapping("/official-alerts/active")
    public ResponseEntity<List<OfficialAlert>> getActiveOfficialAlerts() {
        List<OfficialAlert> active = officialAlertRepository.findByIsExpiredFalseOrderByEffectiveTimeDesc();
        return ResponseEntity.ok(active);
    }

    /**
     * Lists official alerts with optional filtering by severity, hazard, or active status.
     */
    @GetMapping("/official-alerts")
    public ResponseEntity<List<OfficialAlert>> getAllOfficialAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String hazard,
            @RequestParam(required = false, defaultValue = "false") boolean includeExpired) {

        if (!includeExpired) {
            if (severity != null && !severity.trim().isEmpty()) {
                return ResponseEntity.ok(officialAlertRepository.findBySeverityAndIsExpiredFalse(severity.trim()));
            }
            if (hazard != null && !hazard.trim().isEmpty()) {
                return ResponseEntity.ok(officialAlertRepository.findByHazardTypeIgnoreCaseAndIsExpiredFalse(hazard.trim()));
            }
            return ResponseEntity.ok(officialAlertRepository.findByIsExpiredFalseOrderByEffectiveTimeDesc());
        }

        return ResponseEntity.ok(officialAlertRepository.findAllByOrderByPublishedTimeDesc());
    }

    /**
     * Returns real-time health telemetry and sync statistics for the NDMA feed.
     */
    @GetMapping("/official-alerts/status")
    public ResponseEntity<NdmaSyncService.SyncMetrics> getSyncStatus() {
        return ResponseEntity.ok(ndmaSyncService.getMetrics());
    }

    /**
     * Manually triggers immediate synchronization with NDMA SACHET.
     * Restricted to authenticated Administrators.
     */
    @PostMapping("/admin/ndma/sync")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Map<String, Object>> triggerManualSync() {
        NdmaSyncService.SyncMetrics metrics = ndmaSyncService.syncNow();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "NDMA SACHET synchronization completed successfully",
            "metrics", metrics
        ));
    }
}
