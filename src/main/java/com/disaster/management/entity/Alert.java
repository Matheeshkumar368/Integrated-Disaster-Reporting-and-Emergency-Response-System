package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alertId;

    private String title;
    private String message;
    private String disasterType;
    private String severity;
    private String region;
    private String createdBy;
    private LocalDateTime createdAt;
    private String status; // Active, Acknowledged, Resolved
    private String mediaUrl; // URL for uploaded photo/video
    private boolean broadcasted; // Whether alert has been broadcasted to citizens
    
    // Geo-location coordinates
    private Double latitude;
    private Double longitude;

    public Alert() {
        this.createdAt = LocalDateTime.now();
        this.status = "Active";
        this.broadcasted = true; // Auto-broadcast all alerts created by admin
    }

    // Getters and Setters
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDisasterType() { return disasterType; }
    public void setDisasterType(String disasterType) { this.disasterType = disasterType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    
    public boolean isBroadcasted() { return broadcasted; }
    public void setBroadcasted(boolean broadcasted) { this.broadcasted = broadcasted; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
