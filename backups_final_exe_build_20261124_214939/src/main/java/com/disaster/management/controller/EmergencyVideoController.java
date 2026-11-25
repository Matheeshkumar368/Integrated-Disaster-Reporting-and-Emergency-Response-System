package com.disaster.management.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.EmergencyVideo;
import com.disaster.management.service.EmergencyVideoService;

@RestController
@RequestMapping("/api/emergency-videos")
@CrossOrigin(origins = {"*"})
public class EmergencyVideoController {

    private static final Logger log = LoggerFactory.getLogger(EmergencyVideoController.class);

    @Autowired
    private EmergencyVideoService videoService;

    // Public: List and search videos
    @GetMapping
    public ResponseEntity<List<EmergencyVideo>> getVideos(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "language", required = false) String language) {
        List<EmergencyVideo> videos = videoService.searchVideos(search, category, language);
        return ResponseEntity.ok(videos);
    }

    // Public: Contextual recommendations based on active weather alert / hazard type
    @GetMapping("/recommended")
    public ResponseEntity<List<EmergencyVideo>> getRecommendedVideos(
            @RequestParam(value = "hazardType", required = false) String hazardType) {
        return ResponseEntity.ok(videoService.getRecommendedVideos(hazardType));
    }

    // Public: Get specific video details & offline alternative guide
    @GetMapping("/{id}")
    public ResponseEntity<?> getVideoById(@PathVariable Long id) {
        return videoService.getVideoById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // Admin: Create new survival video entry
    @PostMapping
    @PreAuthorize("hasRole('Admin') or hasRole('ADMIN')")
    public ResponseEntity<?> createVideo(@RequestBody EmergencyVideo video) {
        try {
            EmergencyVideo saved = videoService.saveVideo(video);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to save emergency video: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Server error saving video"));
        }
    }

    // Admin: Update existing survival video entry
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin') or hasRole('ADMIN')")
    public ResponseEntity<?> updateVideo(@PathVariable Long id, @RequestBody EmergencyVideo video) {
        try {
            EmergencyVideo updated = videoService.updateVideo(id, video);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update emergency video #{}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Server error updating video"));
        }
    }

    // Admin: Deactivate/Delete video entry
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteVideo(@PathVariable Long id) {
        try {
            videoService.deleteVideo(id);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Video #" + id + " deactivated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Server error deleting video"));
        }
    }
}

