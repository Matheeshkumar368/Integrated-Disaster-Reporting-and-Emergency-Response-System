package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_reports")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    private String title;
    private String description;
    
    private String reportedBy; // Responder email
    
    private String reporterName; // Responder name
    
    private LocalDateTime timestamp;
    
    // Location coordinates
    private Double latitude;
    private Double longitude;
    
    private String locationName;
    
    // Media - stored as JSON array of URLs
    private String imageUrls; // Comma-separated URLs or JSON array
    
    private String reportType; // Status Update, Emergency, Situation Report, etc.
    
    private String status; // Submitted, Under Review, Approved, Archived
    
    private String zone; // Associated zone

    public IncidentReport() {
        this.timestamp = LocalDateTime.now();
        this.status = "Submitted";
    }

    // Getters and Setters
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
}
