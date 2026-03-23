package com.disaster.management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "disasters")
public class Disaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String type;      // e.g. "Flood", "Earthquake", "Fog"

    private String location;  // e.g. "Coimbatore", "Tamil Nadu"

    private String severity;  // e.g. "High", "Medium", "Low"

    private LocalDateTime timestamp;  // when the alert came

    private String status;    // e.g. "Active", "Resolved"
    
    // Geo-location coordinates
    private Double latitude;
    private Double longitude;

    // Empty constructor (needed by Hibernate)
    public Disaster() {}

    // Getters and Setters (copy all these)
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}