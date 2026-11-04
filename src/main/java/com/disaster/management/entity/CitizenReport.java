package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "citizen_reports")
public class CitizenReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    private String citizenEmail;
    private String message;
    private String location;
    
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String photoData; // Base64 encoded photo
    
    private LocalDateTime timestamp;
    private String status; // Pending, Acknowledged, Resolved, Cancelled

    @Column(name = "incident_type")
    private String incidentType = "General";

    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private String title;
    private String severity;
    private String videoUrl;

    @Column(name = "live_tracking_enabled")
    private Boolean liveTrackingEnabled = false;

    @Column(name = "live_latitude")
    private Double liveLatitude;

    @Column(name = "live_longitude")
    private Double liveLongitude;

    @Column(name = "live_accuracy")
    private Double liveAccuracy;

    @Column(name = "live_last_updated_at")
    private LocalDateTime liveLastUpdatedAt;

    @Column(name = "is_archived")
    private Boolean isArchived = false;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deletion_reason")
    private String deletionReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    public CitizenReport() {
        this.timestamp = LocalDateTime.now();
        this.status = "Pending";
        this.incidentType = "General";
        this.isArchived = false;
        this.isDeleted = false;
    }

    // Getters and Setters
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getCitizenEmail() { return citizenEmail; }
    public void setCitizenEmail(String citizenEmail) { this.citizenEmail = citizenEmail; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPhotoData() { return photoData; }
    public void setPhotoData(String photoData) { this.photoData = photoData; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIncidentType() {
        return (incidentType != null && !incidentType.trim().isEmpty()) ? incidentType : "General";
    }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }

    public Boolean getIsArchived() { return isArchived != null ? isArchived : false; }
    public void setIsArchived(Boolean isArchived) { this.isArchived = isArchived; }

    public Boolean getIsDeleted() { return isDeleted != null ? isDeleted : false; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public Boolean getLiveTrackingEnabled() { return liveTrackingEnabled != null ? liveTrackingEnabled : false; }
    public void setLiveTrackingEnabled(Boolean liveTrackingEnabled) { this.liveTrackingEnabled = liveTrackingEnabled; }

    public Double getLiveLatitude() { return liveLatitude; }
    public void setLiveLatitude(Double liveLatitude) { this.liveLatitude = liveLatitude; }

    public Double getLiveLongitude() { return liveLongitude; }
    public void setLiveLongitude(Double liveLongitude) { this.liveLongitude = liveLongitude; }

    public Double getLiveAccuracy() { return liveAccuracy; }
    public void setLiveAccuracy(Double liveAccuracy) { this.liveAccuracy = liveAccuracy; }

    public LocalDateTime getLiveLastUpdatedAt() { return liveLastUpdatedAt; }
    public void setLiveLastUpdatedAt(LocalDateTime liveLastUpdatedAt) { this.liveLastUpdatedAt = liveLastUpdatedAt; }

    public String getDeletionReason() { return deletionReason; }
    public void setDeletionReason(String deletionReason) { this.deletionReason = deletionReason; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
}
