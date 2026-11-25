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
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_recipient", columnList = "recipient_email"),
    @Index(name = "idx_notif_recipient_read", columnList = "recipient_email, is_read"),
    @Index(name = "idx_notif_created_at", columnList = "created_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @Column(name = "recipient_role", length = 50)
    private String recipientRole; // Citizen, Admin, Responder

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(length = 50)
    private String category; // ALERT, UPDATE, RESPONSE, ASSIGNMENT, SYSTEM, USER, DISASTER, MESSAGE

    @Column(length = 20)
    private String priority = "Medium"; // High, Medium, Low

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "related_incident_id")
    private Long relatedIncidentId;

    @Column(name = "related_incident_type", length = 50)
    private String relatedIncidentType; // CITIZEN_REPORT, TASK, ALERT, MESSAGE, SYSTEM

    @Column(name = "action_url", length = 255)
    private String actionUrl;

    @Column(name = "is_deleted_by_recipient")
    private Boolean isDeletedByRecipient = false;

    public Notification() {
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
        this.isDeletedByRecipient = false;
    }

    public Notification(String recipientEmail, String recipientRole, String title, String message, String category, String priority, Long relatedIncidentId, String relatedIncidentType, String actionUrl) {
        this();
        this.recipientEmail = recipientEmail != null ? recipientEmail.trim().toLowerCase() : "";
        this.recipientRole = recipientRole;
        this.title = title;
        this.message = message;
        this.category = category;
        this.priority = priority != null ? priority : "Medium";
        this.relatedIncidentId = relatedIncidentId;
        this.relatedIncidentType = relatedIncidentType;
        this.actionUrl = actionUrl;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail != null ? recipientEmail.trim().toLowerCase() : null;
    }

    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String recipientRole) { this.recipientRole = recipientRole; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Boolean getIsRead() { return isRead != null && isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public Long getRelatedIncidentId() { return relatedIncidentId; }
    public void setRelatedIncidentId(Long relatedIncidentId) { this.relatedIncidentId = relatedIncidentId; }

    public String getRelatedIncidentType() { return relatedIncidentType; }
    public void setRelatedIncidentType(String relatedIncidentType) { this.relatedIncidentType = relatedIncidentType; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public Boolean getIsDeletedByRecipient() { return isDeletedByRecipient != null && isDeletedByRecipient; }
    public void setIsDeletedByRecipient(Boolean isDeletedByRecipient) { this.isDeletedByRecipient = isDeletedByRecipient; }
}
