package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_media", indexes = {
    @Index(name = "idx_media_incident", columnList = "incidentId, incidentType"),
    @Index(name = "idx_media_uploader", columnList = "uploaderEmail"),
    @Index(name = "idx_media_created", columnList = "createdAt")
})
public class IncidentMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;

    private String incidentType = "CITIZEN_REPORT"; // CITIZEN_REPORT, RESCUE_TASK, INCIDENT_REPORT

    @Column(nullable = false)
    private String uploaderEmail;

    private String uploaderName;

    @Column(nullable = false)
    private String uploaderRole; // Citizen, Responder, Admin

    @Column(nullable = false)
    private String mediaType; // PHOTO, VIDEO, AUDIO, DOCUMENT

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String storedFilename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(columnDefinition = "TEXT")
    private String caption;

    private Double captureLatitude;
    private Double captureLongitude;
    private Double captureAccuracy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public IncidentMedia() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }

    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }

    public String getUploaderEmail() { return uploaderEmail; }
    public void setUploaderEmail(String uploaderEmail) { this.uploaderEmail = uploaderEmail; }

    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }

    public String getUploaderRole() { return uploaderRole; }
    public void setUploaderRole(String uploaderRole) { this.uploaderRole = uploaderRole; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public Double getCaptureLatitude() { return captureLatitude; }
    public void setCaptureLatitude(Double captureLatitude) { this.captureLatitude = captureLatitude; }

    public Double getCaptureLongitude() { return captureLongitude; }
    public void setCaptureLongitude(Double captureLongitude) { this.captureLongitude = captureLongitude; }

    public Double getCaptureAccuracy() { return captureAccuracy; }
    public void setCaptureAccuracy(Double captureAccuracy) { this.captureAccuracy = captureAccuracy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

