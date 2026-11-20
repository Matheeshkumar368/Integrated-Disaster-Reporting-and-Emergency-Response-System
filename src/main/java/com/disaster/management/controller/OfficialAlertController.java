package com.disaster.management.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
     * Retrieves a single official alert by database ID.
     */
    @GetMapping("/official-alerts/{id:[0-9]+}")
    public ResponseEntity<?> getAlertById(@PathVariable Long id) {
        return officialAlertRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Retrieves active official alerts for a specific state (e.g., 'Tamil Nadu', 'Assam', 'West Bengal').
     */
    @GetMapping("/official-alerts/state/{state}")
    public ResponseEntity<List<OfficialAlert>> getAlertsByState(@PathVariable String state) {
        if (state == null || state.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(officialAlertRepository.findByStateContainingIgnoreCaseAndIsExpiredFalseOrderByEffectiveTimeDesc(state.trim()));
    }

    /**
     * Retrieves active official alerts for a specific district (e.g., 'Karur', 'Chennai', 'Cuddalore').
     */
    @GetMapping("/official-alerts/district/{district}")
    public ResponseEntity<List<OfficialAlert>> getAlertsByDistrict(@PathVariable String district) {
        if (district == null || district.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(officialAlertRepository.findByDistrictContainingIgnoreCaseAndIsExpiredFalseOrderByEffectiveTimeDesc(district.trim()));
    }

    /**
     * Retrieves official alert history including past/expired alerts.
     */
    @GetMapping("/official-alerts/history")
    public ResponseEntity<List<OfficialAlert>> getAlertHistory() {
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

    /**
     * Authorized test ingestion endpoint for local fixtures or agency feeds.
     */
    @PostMapping(value = "/admin/ndma/test-ingest", consumes = {"application/xml", "text/xml", "text/plain", "application/json"})
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> testIngestXml(
            @RequestBody String rawPayload,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {
        String xml = rawPayload;
        String sourceId = "TEST-" + System.currentTimeMillis();
        String author = "NDMA SACHET (Test Fixture)";

        if (contentType != null && contentType.toLowerCase().contains("json")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<?, ?> map = mapper.readValue(rawPayload, Map.class);
                if (map.containsKey("xml")) xml = (String) map.get("xml");
                if (map.containsKey("sourceId")) sourceId = (String) map.get("sourceId");
                if (map.containsKey("author")) author = (String) map.get("author");
            } catch (Exception e) {
                xml = rawPayload;
            }
        }

        if (xml == null || xml.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "XML content is required"));
        }
        try {
            OfficialAlert alert = ndmaSyncService.ingestXmlPayload(xml, sourceId, "https://sachet.ndma.gov.in/test", author);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "success", true,
                "alertId", alert.getId(),
                "identifier", alert.getCapIdentifier() != null ? alert.getCapIdentifier() : sourceId,
                "alert", alert
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
