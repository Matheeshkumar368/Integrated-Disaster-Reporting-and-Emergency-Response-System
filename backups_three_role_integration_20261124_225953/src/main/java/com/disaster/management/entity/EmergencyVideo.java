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
@Table(name = "emergency_videos")
public class EmergencyVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 32)
    private String youtubeId;

    @Column(nullable = false)
    private String category; // Flood, Fire, Earthquake, Cyclone, Tsunami, Landslide, First Aid, Evacuation, Disaster Kit, Emergency Communication

    @Column(name = "source_organization")
    private String sourceOrganization; // NDMA, NDRF, WHO, USGS, NFPA

    private String language = "English"; // English, Tamil

    @Column(columnDefinition = "TEXT")
    private String description;

    private String duration; // e.g. "4:15"

    @Lob
    @Column(name = "alternative_guide", columnDefinition = "LONGTEXT")
    private String alternativeGuide; // Step-by-step offline guidance

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public EmergencyVideo() {
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    public EmergencyVideo(String title, String youtubeId, String category, String sourceOrganization,
                          String language, String description, String duration, String alternativeGuide) {
        this.title = title;
        this.youtubeId = youtubeId;
        this.category = category;
        this.sourceOrganization = sourceOrganization;
        this.language = language != null ? language : "English";
        this.description = description;
        this.duration = duration;
        this.alternativeGuide = alternativeGuide;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getYoutubeId() { return youtubeId; }
    public void setYoutubeId(String youtubeId) { this.youtubeId = youtubeId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSourceOrganization() { return sourceOrganization; }
    public void setSourceOrganization(String sourceOrganization) { this.sourceOrganization = sourceOrganization; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getAlternativeGuide() { return alternativeGuide; }
    public void setAlternativeGuide(String alternativeGuide) { this.alternativeGuide = alternativeGuide; }

    public Boolean getIsActive() { return isActive != null ? isActive : true; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

