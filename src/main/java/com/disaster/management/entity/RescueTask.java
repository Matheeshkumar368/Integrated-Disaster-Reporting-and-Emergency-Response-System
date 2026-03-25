package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "rescue_tasks", indexes = {
    @Index(name = "idx_assignedTo", columnList = "assignedTo"),
    @Index(name = "idx_zone", columnList = "zone"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_assignedTo_status", columnList = "assignedTo, status"),
    @Index(name = "idx_zone_status", columnList = "zone, status")
})
public class RescueTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    private String title;
    private String description;
    
    private String zone; // Geographic zone for the task
    
    private String priority; // High, Medium, Low
    
    private String status; // Pending, In Progress, Completed
    
    private LocalDateTime deadline;
    
    private LocalDateTime createdAt;
    
    private String createdBy; // Admin email who created the task
    
    private String assignedTo; // Responder email
    
    private String assignedResponderName;
    
    private String notes;
    
    // Location coordinates
    private Double latitude;
    private Double longitude;
    
    private String locationName; // Human-readable location name

    public RescueTask() {
        this.createdAt = LocalDateTime.now();
        this.status = "Pending";
    }

    // Getters and Setters
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getAssignedResponderName() { return assignedResponderName; }
    public void setAssignedResponderName(String assignedResponderName) { this.assignedResponderName = assignedResponderName; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
}
