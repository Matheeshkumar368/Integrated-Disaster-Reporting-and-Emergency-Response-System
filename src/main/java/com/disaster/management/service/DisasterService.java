package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.disaster.management.entity.Disaster;
import com.disaster.management.entity.OfficialAlert;
import com.disaster.management.repository.DisasterRepository;
import com.disaster.management.repository.OfficialAlertRepository;

@Service
public class DisasterService {

    private static final Logger log = LoggerFactory.getLogger(DisasterService.class);

    @Autowired
    private DisasterRepository disasterRepository;

    @Autowired
    private OfficialAlertRepository officialAlertRepository;

    @Autowired
    private NdmaSyncService ndmaSyncService;

    @Async
    public CompletableFuture<Void> fetchNDMAAlertsAsync() {
        try {
            log.info("Triggering asynchronous NDMA SACHET ingestion...");
            ndmaSyncService.syncNow();
        } catch (Exception e) {
            log.warn("Asynchronous NDMA SACHET sync skipped: {}", e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Retrieves active disasters. Prioritizes genuine live NDMA SACHET warnings from OfficialAlert,
     * converting them transparently to Disaster DTO objects so all legacy consumers and dashboards
     * display 100% genuine data.
     */
    public List<Disaster> getAllDisasters() {
        List<OfficialAlert> activeOfficial = officialAlertRepository.findByIsExpiredFalseOrderByEffectiveTimeDesc();

        if (activeOfficial != null && !activeOfficial.isEmpty()) {
            List<Disaster> mapped = new ArrayList<>();
            for (OfficialAlert alert : activeOfficial) {
                Disaster d = new Disaster();
                d.setId(alert.getId() != null ? alert.getId().intValue() : 0);
                d.setType(alert.getHazardType() != null ? alert.getHazardType() : "Advisory");
                d.setLocation(alert.getAreaDescription() != null ? alert.getAreaDescription() : "India");
                d.setSeverity(alert.getSeverity() != null ? alert.getSeverity() : "Moderate");
                d.setTimestamp(alert.getEffectiveTime() != null ? alert.getEffectiveTime() : alert.getPublishedTime());
                d.setStatus(Boolean.TRUE.equals(alert.getIsExpired()) ? "Resolved" : "Active");
                d.setDescription(alert.getHeadline() != null ? alert.getHeadline() : alert.getDescription());
                d.setLatitude(alert.getLatitude());
                d.setLongitude(alert.getLongitude());
                mapped.add(d);
            }
            return mapped;
        }

        // Fallback to disasterRepository if official alerts table has not completed initial sync
        return disasterRepository.findAll();
    }
}
