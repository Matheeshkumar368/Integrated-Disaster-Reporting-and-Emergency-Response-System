package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    private String senderEmail;
    private String senderName;
    private String senderRole; // Citizen, Responder, Admin
    private String receiverEmail;
    private String receiverRole;
    private String message;
    private Long relatedAlertId; // Optional - related to specific alert
    private LocalDateTime sentAt;
    private String status; // Sent, Read

    public Message() {
        this.sentAt = LocalDateTime.now();
        this.status = "Sent";
    }

    // Getters and Setters
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getReceiverEmail() { return receiverEmail; }
    public void setReceiverEmail(String receiverEmail) { this.receiverEmail = receiverEmail; }

    public String getReceiverRole() { return receiverRole; }
    public void setReceiverRole(String receiverRole) { this.receiverRole = receiverRole; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getRelatedAlertId() { return relatedAlertId; }
    public void setRelatedAlertId(Long relatedAlertId) { this.relatedAlertId = relatedAlertId; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
