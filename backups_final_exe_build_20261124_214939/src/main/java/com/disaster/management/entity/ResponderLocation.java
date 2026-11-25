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
@Table(name = "responder_locations", indexes = {
    @Index(name = "idx_loc_session", columnList = "sessionId"),
    @Index(name = "idx_loc_assignment", columnList = "assignmentId"),
    @Index(name = "idx_loc_responder_recorded", columnList = "responderEmail, recordedAt"),
    @Index(name = "idx_loc_sharing_status", columnList = "sharingStatus")
})
public class ResponderLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "responder_id")
    private Long responderId;

    @Column(name = "session_id", nullable = true)
    private Long sessionId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "responder_email", nullable = false)
    private String responderEmail;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Double accuracy;
    private Double heading;
    private Double speed;

    @Column(name = "sharing_status", nullable = false, length = 30)
    private String sharingStatus = "ACTIVE";

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    public ResponderLocation() {}

    public ResponderLocation(Long sessionId, Long assignmentId, String responderEmail,
                             Double latitude, Double longitude, Double accuracy,
                             Double heading, Double speed) {
        this.sessionId = sessionId;
        this.assignmentId = assignmentId;
        this.responderEmail = responderEmail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.heading = heading;
        this.speed = speed;
        this.sharingStatus = "ACTIVE";
        this.recordedAt = LocalDateTime.now();
    }

    public ResponderLocation(Long responderId, Long sessionId, Long assignmentId, String responderEmail,
                             Double latitude, Double longitude, Double accuracy,
                             Double heading, Double speed, String sharingStatus) {
        this.responderId = responderId;
        this.sessionId = sessionId;
        this.assignmentId = assignmentId;
        this.responderEmail = responderEmail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.heading = heading;
        this.speed = speed;
        this.sharingStatus = sharingStatus != null ? sharingStatus : "ACTIVE";
        this.recordedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getResponderId() { return responderId; }
    public void setResponderId(Long responderId) { this.responderId = responderId; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }

    public String getResponderEmail() { return responderEmail; }
    public void setResponderEmail(String responderEmail) { this.responderEmail = responderEmail; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public String getSharingStatus() { return sharingStatus; }
    public void setSharingStatus(String sharingStatus) { this.sharingStatus = sharingStatus; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}

