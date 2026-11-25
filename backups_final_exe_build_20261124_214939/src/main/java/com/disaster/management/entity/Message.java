package com.disaster.management.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_msg_sender", columnList = "sender_email"),
    @Index(name = "idx_msg_receiver", columnList = "receiver_email"),
    @Index(name = "idx_msg_sent_at", columnList = "sent_at")
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    @JsonProperty("id")
    @JsonAlias({"messageId", "id"})
    private Long id;

    @Column(name = "sender_email", nullable = false)
    @JsonProperty("senderEmail")
    private String senderEmail;

    @Column(name = "sender_name")
    @JsonProperty("senderName")
    private String senderName;

    @Column(name = "sender_role")
    @JsonProperty("senderRole")
    private String senderRole; // Citizen, Responder, Admin

    @Column(name = "receiver_email", nullable = false)
    @JsonProperty("recipientEmail")
    @JsonAlias({"receiverEmail", "recipientEmail"})
    private String recipientEmail;

    @Column(name = "receiver_role")
    @JsonProperty("recipientRole")
    @JsonAlias({"receiverRole", "recipientRole"})
    private String recipientRole;

    @Column(name = "subject")
    @JsonProperty("subject")
    private String subject;

    @Column(name = "message", columnDefinition = "TEXT")
    @JsonProperty("messageBody")
    @JsonAlias({"message", "messageBody", "content", "text"})
    private String messageBody;

    @Column(name = "related_alert_id")
    @JsonProperty("relatedAlertId")
    private Long relatedAlertId;

    @Column(name = "sent_at")
    @JsonProperty("createdAt")
    @JsonAlias({"sentAt", "createdAt"})
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    @JsonProperty("readAt")
    private LocalDateTime readAt;

    @Column(name = "status")
    @JsonProperty("messageStatus")
    @JsonAlias({"status", "messageStatus"})
    private String messageStatus = "Sent"; // Sent, Read

    @Column(name = "is_deleted_by_sender")
    @JsonProperty("isDeletedBySender")
    private Boolean isDeletedBySender = false;

    @Column(name = "is_deleted_by_recipient")
    @JsonProperty("isDeletedByRecipient")
    private Boolean isDeletedByRecipient = false;

    public Message() {
        this.createdAt = LocalDateTime.now();
        this.messageStatus = "Sent";
        this.isDeletedBySender = false;
        this.isDeletedByRecipient = false;
    }

    public Message(String senderEmail, String senderRole, String recipientEmail, String recipientRole, String subject, String messageBody) {
        this();
        this.senderEmail = senderEmail != null ? senderEmail.trim().toLowerCase() : null;
        this.senderRole = senderRole;
        this.recipientEmail = recipientEmail != null ? recipientEmail.trim().toLowerCase() : null;
        this.recipientRole = recipientRole;
        this.subject = subject != null ? subject.trim() : null;
        this.messageBody = messageBody != null ? messageBody.trim() : null;
    }

    // Getters and Setters with dual support

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return id; }
    public void setMessageId(Long id) { this.id = id; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { 
        this.senderEmail = senderEmail != null ? senderEmail.trim().toLowerCase() : null; 
    }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { 
        this.recipientEmail = recipientEmail != null ? recipientEmail.trim().toLowerCase() : null; 
    }
    public String getReceiverEmail() { return recipientEmail; }
    public void setReceiverEmail(String receiverEmail) { setRecipientEmail(receiverEmail); }

    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String recipientRole) { this.recipientRole = recipientRole; }
    public String getReceiverRole() { return recipientRole; }
    public void setReceiverRole(String receiverRole) { setRecipientRole(receiverRole); }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessageBody() { return messageBody; }
    public void setMessageBody(String messageBody) { this.messageBody = messageBody; }
    public String getMessage() { return messageBody; }
    public void setMessage(String message) { this.messageBody = message; }

    public Long getRelatedAlertId() { return relatedAlertId; }
    public void setRelatedAlertId(Long relatedAlertId) { this.relatedAlertId = relatedAlertId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSentAt() { return createdAt; }
    public void setSentAt(LocalDateTime sentAt) { this.createdAt = sentAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public String getMessageStatus() { return messageStatus; }
    public void setMessageStatus(String messageStatus) { this.messageStatus = messageStatus; }
    public String getStatus() { return messageStatus; }
    public void setStatus(String status) { this.messageStatus = status; }

    public Boolean getIsDeletedBySender() { return isDeletedBySender != null ? isDeletedBySender : false; }
    public void setIsDeletedBySender(Boolean isDeletedBySender) { this.isDeletedBySender = isDeletedBySender; }

    public Boolean getIsDeletedByRecipient() { return isDeletedByRecipient != null ? isDeletedByRecipient : false; }
    public void setIsDeletedByRecipient(Boolean isDeletedByRecipient) { this.isDeletedByRecipient = isDeletedByRecipient; }
}
