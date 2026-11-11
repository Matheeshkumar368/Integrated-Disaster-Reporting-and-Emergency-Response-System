package com.disaster.management.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.IncidentMedia;
import com.disaster.management.entity.User;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.service.MediaStorageService;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = {"*"})
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    @Autowired
    private MediaStorageService mediaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CitizenReportRepository reportRepository;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "incidentId", required = false) Long incidentId,
            @RequestParam(value = "incidentType", defaultValue = "CITIZEN_REPORT") String incidentType,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "accuracy", required = false) Double accuracy) {

        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            response.put("status", "error");
            response.put("message", "Authentication required to upload media.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String userEmail = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse("Citizen");

        String userName = userEmail;
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isPresent() && userOpt.get().getName() != null) {
            userName = userOpt.get().getName();
        }

        try {
            IncidentMedia media = mediaService.storeMedia(
                file,
                incidentId,
                incidentType,
                userEmail,
                userName,
                role,
                latitude,
                longitude,
                accuracy,
                caption
            );

            response.put("status", "success");
            response.put("message", "Media uploaded and indexed successfully.");
            response.put("mediaId", media.getId());
            response.put("mediaType", media.getMediaType());
            response.put("originalFilename", media.getOriginalFilename());
            response.put("storedFilename", media.getStoredFilename());
            response.put("fileSize", media.getFileSizeBytes());
            response.put("downloadUrl", "/api/media/" + media.getId() + "/download");
            response.put("streamUrl", "/api/media/file/" + media.getStoredFilename());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("Media upload rejected: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Failed to store media file: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "Failed to upload media file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMediaMetadata(@PathVariable Long id) {
        Optional<IncidentMedia> mediaOpt = mediaService.getMediaById(id);
        if (mediaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", "Media not found"));
        }
        return ResponseEntity.ok(mediaOpt.get());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadMediaById(@PathVariable Long id) {
        Optional<IncidentMedia> mediaOpt = mediaService.getMediaById(id);
        if (mediaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        IncidentMedia media = mediaOpt.get();
        Resource file = mediaService.loadAsResource(media.getStoredFilename());

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(media.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getOriginalFilename() + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
            .body(file);
    }

    @GetMapping("/file/{filename:.+}")
    public ResponseEntity<?> serveMediaFile(@PathVariable String filename) {
        try {
            Resource file = mediaService.loadAsResource(filename);
            String contentType = "application/octet-stream";
            String lower = filename.toLowerCase();

            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (lower.endsWith(".png")) contentType = "image/png";
            else if (lower.endsWith(".webp")) contentType = "image/webp";
            else if (lower.endsWith(".gif")) contentType = "image/gif";
            else if (lower.endsWith(".mp4")) contentType = "video/mp4";
            else if (lower.endsWith(".webm")) contentType = "video/webm";
            else if (lower.endsWith(".mp3")) contentType = "audio/mpeg";
            else if (lower.endsWith(".wav")) contentType = "audio/wav";
            else if (lower.endsWith(".pdf")) contentType = "application/pdf";

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(file);
        } catch (Exception e) {
            log.warn("Media file not found or unreadable: {}", filename);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "error", "message", "File not found: " + filename));
        }
    }

    @GetMapping("/incident/{incidentId}")
    public ResponseEntity<List<IncidentMedia>> getMediaByIncident(
            @PathVariable Long incidentId,
            @RequestParam(value = "type", required = false) String type) {

        List<IncidentMedia> list;
        if (type != null && !type.isEmpty()) {
            list = mediaService.getMediaByIncidentAndType(incidentId, type);
        } else {
            list = mediaService.getMediaByIncident(incidentId);
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<IncidentMedia>> getRecentMedia() {
        return ResponseEntity.ok(mediaService.getRecentMedia());
    }

    // Delete uploaded evidence (Uploader if report unverified, or Admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMedia(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Optional<IncidentMedia> mediaOpt = mediaService.getMediaById(id);
        if (mediaOpt.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Media not found with ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        IncidentMedia media = mediaOpt.get();
        String callerEmail = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        boolean isUploader = callerEmail.equalsIgnoreCase(media.getUploaderEmail());

        if (!isAdmin && !isUploader) {
            response.put("status", "error");
            response.put("message", "Forbidden: You are not authorized to delete evidence uploaded by " + media.getUploaderEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Integrity check: if linked to CitizenReport, check if verified/assigned.
        // Citizens can only delete media if the report is unverified/pending.
        if (!isAdmin && "CITIZEN_REPORT".equalsIgnoreCase(media.getIncidentType()) && media.getIncidentId() != null) {
            Optional<CitizenReport> reportOpt = reportRepository.findById(media.getIncidentId());
            if (reportOpt.isPresent()) {
                CitizenReport report = reportOpt.get();
                if (!"Pending".equalsIgnoreCase(report.getStatus()) || report.getAssignedResponderEmail() != null) {
                    response.put("status", "error");
                    response.put("message", "Cannot delete evidence: Incident #" + report.getReportId() + " is already verified or under active rescue operation.");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
                }
            }
        }

        // Integrity check: if linked to RescueTask, non-admin cannot delete
        if (!isAdmin && "RESCUE_TASK".equalsIgnoreCase(media.getIncidentType())) {
            response.put("status", "error");
            response.put("message", "Forbidden: Evidence attached to active rescue missions cannot be deleted by responders.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            mediaService.deleteMedia(id);
            response.put("status", "success");
            response.put("message", "Evidence/media deleted successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete media #{}: {}", id, e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to delete media: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

