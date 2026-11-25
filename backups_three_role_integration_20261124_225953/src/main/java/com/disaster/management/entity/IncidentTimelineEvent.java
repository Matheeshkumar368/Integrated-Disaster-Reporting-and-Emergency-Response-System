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
@Table(name = "incident_timeline_events", indexes = {
    @Index(name = "idx_timeline_incident", columnList = "incidentId, incidentType"),
    @Index(name = "idx_timeline_created", columnList = "createdAt")
})
public class IncidentTimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false, length = 50)
    private String incidentType = "CITIZEN_REPORT"; // CITIZEN_REPORT, RESCUE_TASK

    @Column(nullable = false, length = 50)
    private String eventType; // REPORTED, VERIFIED, ASSIGNED, EN_ROUTE, ON_SCENE, RESOLVED, MEDIA_UPLOADED, MESSAGE

    @Column(nullable = false)
    private String actorEmail;

    @Column(nullable = false, length = 50)
    private String actorRole; // Citizen, Responder, Admin, System

    private String actorName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    private Long mediaId;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public IncidentTimelineEvent() {}

    public IncidentTimelineEvent(Long incidentId, String incidentType, String eventType,
                                 String actorEmail, String actorRole, String actorName,
                                 String message, Long mediaId, String payloadJson) {
        this.incidentId = incidentId;
        this.incidentType = incidentType != null ? incidentType : "CITIZEN_REPORT";
        this.eventType = eventType;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.actorName = actorName;
        this.message = message;
        this.mediaId = mediaId;
        this.payloadJson = payloadJson;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }

    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long mediaId) { this.mediaId = mediaId; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

