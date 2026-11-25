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
@Table(name = "emergency_call_sessions")
public class EmergencyCallSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", unique = true, nullable = false, length = 64)
    private String callId;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "responder_email", nullable = false)
    private String responderEmail;

    @Column(name = "responder_name")
    private String responderName;

    @Column(name = "citizen_email", nullable = false)
    private String citizenEmail;

    @Column(name = "citizen_name")
    private String citizenName;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // RINGING, CONNECTED, REJECTED, ENDED, MISSED

    @Lob
    @Column(name = "sdp_offer", columnDefinition = "TEXT")
    private String sdpOffer;

    @Lob
    @Column(name = "sdp_answer", columnDefinition = "TEXT")
    private String sdpAnswer;

    @Lob
    @Column(name = "responder_candidates_json", columnDefinition = "TEXT")
    private String responderCandidatesJson;

    @Lob
    @Column(name = "citizen_candidates_json", columnDefinition = "TEXT")
    private String citizenCandidatesJson;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public EmergencyCallSession() {
        this.startedAt = LocalDateTime.now();
        this.status = "RINGING";
    }

    public EmergencyCallSession(String callId, Long incidentId, String responderEmail, String responderName,
                                String citizenEmail, String citizenName, String sdpOffer) {
        this.callId = callId;
        this.incidentId = incidentId;
        this.responderEmail = responderEmail;
        this.responderName = responderName;
        this.citizenEmail = citizenEmail;
        this.citizenName = citizenName;
        this.sdpOffer = sdpOffer;
        this.status = "RINGING";
        this.startedAt = LocalDateTime.now();
        this.responderCandidatesJson = "[]";
        this.citizenCandidatesJson = "[]";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }

    public String getResponderEmail() { return responderEmail; }
    public void setResponderEmail(String responderEmail) { this.responderEmail = responderEmail; }

    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }

    public String getCitizenEmail() { return citizenEmail; }
    public void setCitizenEmail(String citizenEmail) { this.citizenEmail = citizenEmail; }

    public String getCitizenName() { return citizenName; }
    public void setCitizenName(String citizenName) { this.citizenName = citizenName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSdpOffer() { return sdpOffer; }
    public void setSdpOffer(String sdpOffer) { this.sdpOffer = sdpOffer; }

    public String getSdpAnswer() { return sdpAnswer; }
    public void setSdpAnswer(String sdpAnswer) { this.sdpAnswer = sdpAnswer; }

    public String getResponderCandidatesJson() { return responderCandidatesJson; }
    public void setResponderCandidatesJson(String responderCandidatesJson) { this.responderCandidatesJson = responderCandidatesJson; }

    public String getCitizenCandidatesJson() { return citizenCandidatesJson; }
    public void setCitizenCandidatesJson(String citizenCandidatesJson) { this.citizenCandidatesJson = citizenCandidatesJson; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getConnectedAt() { return connectedAt; }
    public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
