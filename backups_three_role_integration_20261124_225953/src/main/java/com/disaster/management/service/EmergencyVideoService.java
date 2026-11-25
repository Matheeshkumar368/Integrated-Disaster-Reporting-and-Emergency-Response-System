package com.disaster.management.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.EmergencyVideo;
import com.disaster.management.repository.EmergencyVideoRepository;

@Service
public class EmergencyVideoService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyVideoService.class);

    @Autowired
    private EmergencyVideoRepository videoRepository;

    public List<EmergencyVideo> getAllActiveVideos() {
        return videoRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    public List<EmergencyVideo> searchVideos(String search, String category, String language) {
        return videoRepository.searchVideos(
            (search != null && !search.trim().isEmpty()) ? search.trim() : null,
            (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) ? category.trim() : null,
            (language != null && !language.trim().isEmpty() && !"ALL".equalsIgnoreCase(language)) ? language.trim() : null
        );
    }

    public Optional<EmergencyVideo> getVideoById(Long id) {
        return videoRepository.findById(id);
    }

    public List<EmergencyVideo> getRecommendedVideos(String hazardType) {
        if (hazardType == null || hazardType.trim().isEmpty()) {
            return videoRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        }
        String cleanType = hazardType.trim().toLowerCase();
        String targetCategory = "Flood";
        if (cleanType.contains("rain") || cleanType.contains("flood") || cleanType.contains("water")) {
            targetCategory = "Flood";
        } else if (cleanType.contains("fire") || cleanType.contains("burn") || cleanType.contains("smoke")) {
            targetCategory = "Fire";
        } else if (cleanType.contains("quake") || cleanType.contains("seismic") || cleanType.contains("tremor")) {
            targetCategory = "Earthquake";
        } else if (cleanType.contains("cyclone") || cleanType.contains("storm") || cleanType.contains("wind") || cleanType.contains("hurricane")) {
            targetCategory = "Cyclone";
        } else if (cleanType.contains("tsunami") || cleanType.contains("wave")) {
            targetCategory = "Tsunami";
        } else if (cleanType.contains("landslide") || cleanType.contains("mud")) {
            targetCategory = "Landslide";
        } else if (cleanType.contains("medical") || cleanType.contains("first aid") || cleanType.contains("injury")) {
            targetCategory = "First Aid";
        }

        List<EmergencyVideo> matched = videoRepository.findByCategoryContainingIgnoreCaseAndIsActiveTrue(targetCategory);
        if (matched.isEmpty()) {
            return videoRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        }
        return matched;
    }

    @Transactional
    public EmergencyVideo saveVideo(EmergencyVideo video) {
        if (video.getTitle() == null || video.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Video title is required");
        }
        if (video.getYoutubeId() == null || video.getYoutubeId().trim().isEmpty()) {
            throw new IllegalArgumentException("YouTube Video ID is required");
        }
        if (video.getCategory() == null || video.getCategory().trim().isEmpty()) {
            video.setCategory("Evacuation");
        }
        // Extract ID if a full YouTube URL was pasted
        String yId = video.getYoutubeId().trim();
        if (yId.contains("v=")) {
            yId = yId.substring(yId.indexOf("v=") + 2);
            int ampersandPos = yId.indexOf('&');
            if (ampersandPos != -1) yId = yId.substring(0, ampersandPos);
        } else if (yId.contains("youtu.be/")) {
            yId = yId.substring(yId.indexOf("youtu.be/") + 9);
            int qPos = yId.indexOf('?');
            if (qPos != -1) yId = yId.substring(0, qPos);
        }
        video.setYoutubeId(yId);
        return videoRepository.save(video);
    }

    @Transactional
    public EmergencyVideo updateVideo(Long id, EmergencyVideo updated) {
        EmergencyVideo existing = videoRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Video not found with ID: " + id));

        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getYoutubeId() != null) {
            String yId = updated.getYoutubeId().trim();
            if (yId.contains("v=")) {
                yId = yId.substring(yId.indexOf("v=") + 2);
                int ampersandPos = yId.indexOf('&');
                if (ampersandPos != -1) yId = yId.substring(0, ampersandPos);
            } else if (yId.contains("youtu.be/")) {
                yId = yId.substring(yId.indexOf("youtu.be/") + 9);
                int qPos = yId.indexOf('?');
                if (qPos != -1) yId = yId.substring(0, qPos);
            }
            existing.setYoutubeId(yId);
        }
        if (updated.getCategory() != null) existing.setCategory(updated.getCategory());
        if (updated.getSourceOrganization() != null) existing.setSourceOrganization(updated.getSourceOrganization());
        if (updated.getLanguage() != null) existing.setLanguage(updated.getLanguage());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getDuration() != null) existing.setDuration(updated.getDuration());
        if (updated.getAlternativeGuide() != null) existing.setAlternativeGuide(updated.getAlternativeGuide());
        if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

        return videoRepository.save(existing);
    }

    @Transactional
    public void deleteVideo(Long id) {
        EmergencyVideo existing = videoRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Video not found with ID: " + id));
        existing.setIsActive(false);
        videoRepository.save(existing);
    }
}
