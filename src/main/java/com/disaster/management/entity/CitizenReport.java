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
    private String status; // Pending, Acknowledged, Resolved

    public CitizenReport() {
        this.timestamp = LocalDateTime.now();
        this.status = "Pending";
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
}
