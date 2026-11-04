package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * OfficialAlert Entity
 * Persists genuine disaster warnings imported from official government feeds
 * (NDMA SACHET Common Alerting Protocol - CAP 1.2 RSS feeds).
 * Strictly distinguished from citizen reports and administrator-created announcements.
 */
@Entity
@Table(name = "official_alerts", indexes = {
    @Index(name = "idx_official_source_id", columnList = "sourceAlertId", unique = true),
    @Index(name = "idx_official_hazard", columnList = "hazardType"),
    @Index(name = "idx_official_severity", columnList = "severity"),
    @Index(name = "idx_official_expires", columnList = "expiresTime"),
    @Index(name = "idx_official_expired", columnList = "isExpired"),
    @Index(name = "idx_official_sync", columnList = "syncTimestamp")
})
public class OfficialAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique source identifier from NDMA/CAP feed (e.g., "1793294058125010")
    @Column(nullable = false, unique = true, length = 100)
    private String sourceAlertId;

    // Full CAP identifier (e.g., "IN-1793294058125010_44")
    @Column(length = 150)
    private String capIdentifier;

    // Originating authority / agency (e.g., "IMD-Guwahati", "Central Water Commission", "NDMA")
    @Column(length = 150)
    private String sourceAgency;

    // URL link to original CAP XML file on sachet.ndma.gov.in
    @Column(length = 500)
    private String sourceUrl;

    // Brief headline summary
    @Column(length = 1000)
    private String headline;

    // Full alert text and meteorological advisory
    @Column(columnDefinition = "TEXT")
    private String description;

    // Official safety guidelines / instructions from SDMA/NDMA
    @Column(columnDefinition = "TEXT")
    private String instruction;

    // Standardized event hazard type (e.g., "Thunderstorm with Lightning", "Flood", "Cyclone")
    @Column(length = 100)
    private String hazardType;

    // CAP Severity: Extreme, Severe, Moderate, Minor, Unknown
    @Column(length = 50)
    private String severity;

    // CAP Urgency: Immediate, Expected, Future, Past, Unknown
    @Column(length = 50)
    private String urgency;

    // CAP Certainty: Observed, Likely, Possible, Unlikely, Unknown
    @Column(length = 50)
    private String certainty;

    // Affected geographic region description (e.g., "Cachar district of Assam")
    @Column(columnDefinition = "TEXT")
    private String areaDescription;

    // Geographic latitude (centroid or source-provided)
    private Double latitude;

    // Geographic longitude (centroid or source-provided)
    private Double longitude;

    // Precision flag: EXACT_COORDINATES, DISTRICT_CENTROID, STATE_CENTROID
    @Column(length = 50)
    private String locationPrecision;

    // CAP Timestamps
    private LocalDateTime publishedTime;
    private LocalDateTime effectiveTime;
    private LocalDateTime onset;
    private LocalDateTime expiresTime;

    // Source message status: Actual, Exercise, System, Test, Draft
    @Column(length = 50)
    private String sourceStatus;

    // CAP Message Type: Alert, Update, Cancel, Ack, Error
    @Column(length = 50)
    private String messageType;

    // Dynamic lifecycle indicator: true if current time exceeds expiresTime
    @Column(nullable = false)
    private Boolean isExpired = false;

    // Ingestion metadata
    private LocalDateTime syncTimestamp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OfficialAlert() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.syncTimestamp == null) {
            this.syncTimestamp = LocalDateTime.now();
        }
        if (this.isExpired == null) {
            this.isExpired = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceAlertId() { return sourceAlertId; }
    public void setSourceAlertId(String sourceAlertId) { this.sourceAlertId = sourceAlertId; }

    public String getCapIdentifier() { return capIdentifier; }
    public void setCapIdentifier(String capIdentifier) { this.capIdentifier = capIdentifier; }

    public String getSourceAgency() { return sourceAgency; }
    public void setSourceAgency(String sourceAgency) { this.sourceAgency = sourceAgency; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getHazardType() { return hazardType; }
    public void setHazardType(String hazardType) { this.hazardType = hazardType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getCertainty() { return certainty; }
    public void setCertainty(String certainty) { this.certainty = certainty; }

    public String getAreaDescription() { return areaDescription; }
    public void setAreaDescription(String areaDescription) { this.areaDescription = areaDescription; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationPrecision() { return locationPrecision; }
    public void setLocationPrecision(String locationPrecision) { this.locationPrecision = locationPrecision; }

    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }

    public LocalDateTime getEffectiveTime() { return effectiveTime; }
    public void setEffectiveTime(LocalDateTime effectiveTime) { this.effectiveTime = effectiveTime; }

    public LocalDateTime getOnset() { return onset; }
    public void setOnset(LocalDateTime onset) { this.onset = onset; }

    public LocalDateTime getExpiresTime() { return expiresTime; }
    public void setExpiresTime(LocalDateTime expiresTime) { this.expiresTime = expiresTime; }

    public String getSourceStatus() { return sourceStatus; }
    public void setSourceStatus(String sourceStatus) { this.sourceStatus = sourceStatus; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public Boolean getIsExpired() { return isExpired; }
    public void setIsExpired(Boolean isExpired) { this.isExpired = isExpired; }

    public LocalDateTime getSyncTimestamp() { return syncTimestamp; }
    public void setSyncTimestamp(LocalDateTime syncTimestamp) { this.syncTimestamp = syncTimestamp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

