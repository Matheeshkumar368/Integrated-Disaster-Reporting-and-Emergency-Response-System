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
@Table(name = "tracking_sessions", indexes = {
    @Index(name = "idx_tracking_assignment", columnList = "assignmentId"),
    @Index(name = "idx_tracking_responder", columnList = "responderEmail"),
    @Index(name = "idx_tracking_status", columnList = "status")
})
public class TrackingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assignmentId; // Associated RescueTask taskId

    @Column(nullable = false)
    private String responderEmail;

    private String responderName;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, PAUSED, COMPLETED, DISCONNECTED

    private Double destinationLatitude;
    private Double destinationLongitude;
    private String destinationAddress;

    private Double currentLatitude;
    private Double currentLongitude;
    private Double currentAccuracy;
    private Double currentHeading;
    private Double currentSpeed;

    private Double estimatedDistanceMeters;
    private Integer estimatedDurationSeconds;

    private LocalDateTime lastHeartbeatAt;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    public TrackingSession() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }

    public String getResponderEmail() { return responderEmail; }
    public void setResponderEmail(String responderEmail) { this.responderEmail = responderEmail; }

    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getDestinationLatitude() { return destinationLatitude; }
    public void setDestinationLatitude(Double destinationLatitude) { this.destinationLatitude = destinationLatitude; }

    public Double getDestinationLongitude() { return destinationLongitude; }
    public void setDestinationLongitude(Double destinationLongitude) { this.destinationLongitude = destinationLongitude; }

    public String getDestinationAddress() { return destinationAddress; }
    public void setDestinationAddress(String destinationAddress) { this.destinationAddress = destinationAddress; }

    public Double getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(Double currentLatitude) { this.currentLatitude = currentLatitude; }

    public Double getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(Double currentLongitude) { this.currentLongitude = currentLongitude; }

    public Double getCurrentAccuracy() { return currentAccuracy; }
    public void setCurrentAccuracy(Double currentAccuracy) { this.currentAccuracy = currentAccuracy; }

    public Double getCurrentHeading() { return currentHeading; }
    public void setCurrentHeading(Double currentHeading) { this.currentHeading = currentHeading; }

    public Double getCurrentSpeed() { return currentSpeed; }
    public void setCurrentSpeed(Double currentSpeed) { this.currentSpeed = currentSpeed; }

    public Double getEstimatedDistanceMeters() { return estimatedDistanceMeters; }
    public void setEstimatedDistanceMeters(Double estimatedDistanceMeters) { this.estimatedDistanceMeters = estimatedDistanceMeters; }

    public Integer getEstimatedDurationSeconds() { return estimatedDurationSeconds; }
    public void setEstimatedDurationSeconds(Integer estimatedDurationSeconds) { this.estimatedDurationSeconds = estimatedDurationSeconds; }

    public LocalDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}

