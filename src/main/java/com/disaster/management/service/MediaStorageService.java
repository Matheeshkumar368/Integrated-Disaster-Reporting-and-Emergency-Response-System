package com.disaster.management.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.disaster.management.entity.IncidentMedia;
import com.disaster.management.repository.IncidentMediaRepository;

import jakarta.annotation.PostConstruct;

@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        "jpg", "jpeg", "png", "webp", "gif",
        "mp4", "webm", "mov", "m4v",
        "mp3", "wav", "ogg", "m4a",
        "pdf"
    ));

    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
        "image/jpeg", "image/png", "image/webp", "image/gif",
        "video/mp4", "video/webm", "video/quicktime", "video/x-m4v",
        "audio/webm", "audio/mpeg", "audio/wav", "audio/ogg", "audio/mp4",
        "application/pdf", "application/octet-stream"
    ));

    @Value("${app.media.upload-dir:uploads/incident-media}")
    private String uploadDirStr;

    private Path rootLocation;

    @Autowired
    private IncidentMediaRepository mediaRepository;

    @Autowired
    private IncidentTimelineService timelineService;

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadDirStr).toAbsolutePath().normalize();
            Files.createDirectories(this.rootLocation);
            log.info("Media storage directory initialized at: {}", this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage folder " + uploadDirStr, e);
        }
    }

    @Transactional
    public IncidentMedia storeMedia(
            MultipartFile file,
            Long incidentId,
            String incidentType,
            String uploaderEmail,
            String uploaderName,
            String uploaderRole,
            Double latitude,
            Double longitude,
            Double accuracy,
            String caption) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            originalFilename = "capture_" + System.currentTimeMillis();
        }

        // Clean filename and extract extension
        String cleanOriginalName = Paths.get(originalFilename).getFileName().toString();
        String extension = "";
        int extIdx = cleanOriginalName.lastIndexOf('.');
        if (extIdx > 0 && extIdx < cleanOriginalName.length() - 1) {
            extension = cleanOriginalName.substring(extIdx + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File extension ." + extension + " is not allowed for security reasons.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isEmpty() && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("Non-standard content type detected: {}, defaulting check to extension", contentType);
        }

        String mediaType = determineMediaType(extension, contentType);

        // Generate safe unique filename
        String storedFilename = UUID.randomUUID().toString() + "_" + cleanOriginalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path destinationFile = this.rootLocation.resolve(storedFilename).normalize().toAbsolutePath();

        // Path-traversal defense check
        if (!destinationFile.startsWith(this.rootLocation)) {
            throw new SecurityException("Cannot store file outside current storage directory.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        IncidentMedia media = new IncidentMedia();
        media.setIncidentId(incidentId);
        media.setIncidentType(incidentType != null ? incidentType : "CITIZEN_REPORT");
        media.setUploaderEmail(uploaderEmail);
        media.setUploaderName(uploaderName != null ? uploaderName : uploaderEmail);
        media.setUploaderRole(uploaderRole);
        media.setMediaType(mediaType);
        media.setFilePath(destinationFile.toString());
        media.setOriginalFilename(cleanOriginalName);
        media.setStoredFilename(storedFilename);
        media.setContentType(contentType != null ? contentType : "application/octet-stream");
        media.setFileSizeBytes(file.getSize());
        media.setCaption(caption);
        media.setCaptureLatitude(latitude);
        media.setCaptureLongitude(longitude);
        media.setCaptureAccuracy(accuracy);
        media.setCreatedAt(LocalDateTime.now());

        IncidentMedia savedMedia = mediaRepository.save(media);

        // Automatically log timeline event if attached to an incident
        if (incidentId != null && incidentId > 0) {
            String noteMsg = (uploaderName != null ? uploaderName : uploaderEmail) + " uploaded " + mediaType.toLowerCase() + " evidence: " + cleanOriginalName;
            if (caption != null && !caption.trim().isEmpty()) {
                noteMsg += " - \"" + caption + "\"";
            }
            timelineService.logEvent(
                incidentId,
                incidentType,
                "MEDIA_UPLOADED",
                uploaderEmail,
                uploaderRole,
                uploaderName,
                noteMsg,
                savedMedia.getId(),
                "{\"mediaType\":\"" + mediaType + "\",\"storedFilename\":\"" + storedFilename + "\"}"
            );
        }

        log.info("Media stored successfully: {} ({} bytes, id: {})", storedFilename, file.getSize(), savedMedia.getId());
        return savedMedia;
    }

    public Resource loadAsResource(String storedFilename) {
        try {
            Path file = rootLocation.resolve(storedFilename).normalize().toAbsolutePath();
            if (!file.startsWith(this.rootLocation)) {
                throw new SecurityException("Cannot access file outside current directory.");
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + storedFilename);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not read file: " + storedFilename, e);
        }
    }

    public Optional<IncidentMedia> getMediaById(Long id) {
        return mediaRepository.findById(id);
    }

    public List<IncidentMedia> getMediaByIncident(Long incidentId) {
        return mediaRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
    }

    public List<IncidentMedia> getMediaByIncidentAndType(Long incidentId, String incidentType) {
        return mediaRepository.findByIncidentIdAndIncidentTypeOrderByCreatedAtDesc(incidentId, incidentType);
    }

    public List<IncidentMedia> getRecentMedia() {
        return mediaRepository.findTop50ByOrderByCreatedAtDesc();
    }

    private String determineMediaType(String extension, String contentType) {
        if (Arrays.asList("jpg", "jpeg", "png", "webp", "gif").contains(extension)) {
            return "PHOTO";
        }
        if (Arrays.asList("mp4", "webm", "mov", "m4v").contains(extension)) {
            return "VIDEO";
        }
        if (Arrays.asList("mp3", "wav", "ogg", "m4a").contains(extension)) {
            return "AUDIO";
        }
        return "DOCUMENT";
    }
}

