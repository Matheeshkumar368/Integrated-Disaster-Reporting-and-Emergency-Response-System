package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.ResponderLocation;
import com.disaster.management.entity.TrackingSession;
import com.disaster.management.entity.User;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.ResponderLocationRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class ResponderLocationService {

    private static final Logger log = LoggerFactory.getLogger(ResponderLocationService.class);

    @Autowired
    private ResponderLocationRepository locationRepository;

    @Autowired
    private RescueTaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResponderTrackingService trackingService;

    @Transactional
    public ResponderLocation startLocationSharing(Long taskId, String responderEmail, Double lat, Double lng, Double accuracy) {
        validateCoordinates(lat, lng);

        User user = userRepository.findByEmail(responderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Responder not found: " + responderEmail));

        Long resolvedTaskId = validateAndResolveTask(taskId, responderEmail, user.getRole());

        // Coordinate with live tracking session engine
        Long sessionId = null;
        try {
            TrackingSession session = trackingService.startTracking(
                resolvedTaskId, responderEmail, user.getName(), lat, lng, accuracy
            );
            if (session != null) {
                sessionId = session.getId();
            }
        } catch (Exception e) {
            log.warn("TrackingSession initialization note: {}", e.getMessage());
        }

        ResponderLocation loc = new ResponderLocation(
            user.getId(),
            sessionId,
            resolvedTaskId,
            responderEmail,
            lat,
            lng,
            accuracy,
            0.0,
            0.0,
            "ACTIVE"
        );

        ResponderLocation saved = locationRepository.save(loc);
        log.info("Responder {} started live GPS sharing for task #{} at [{}, {}]", responderEmail, resolvedTaskId, lat, lng);
        return saved;
    }

    @Transactional
    public ResponderLocation updateLocation(Long taskId, String responderEmail, Double lat, Double lng,
                                           Double accuracy, Double heading, Double speed) {
        validateCoordinates(lat, lng);

        User user = userRepository.findByEmail(responderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Responder not found: " + responderEmail));

        Long resolvedTaskId = validateAndResolveTask(taskId, responderEmail, user.getRole());

        Long sessionId = null;
        try {
            TrackingSession session = trackingService.updateLocation(
                resolvedTaskId, responderEmail, lat, lng, accuracy, heading, speed
            );
            if (session != null) {
                sessionId = session.getId();
            }
        } catch (Exception e) {
            log.warn("TrackingSession update note: {}", e.getMessage());
        }

        ResponderLocation loc = new ResponderLocation(
            user.getId(),
            sessionId,
            resolvedTaskId,
            responderEmail,
            lat,
            lng,
            accuracy,
            heading,
            speed,
            "ACTIVE"
        );

        ResponderLocation saved = locationRepository.save(loc);
        log.debug("Responder {} transmitted GPS update for task #{}: [{}, {}]", responderEmail, resolvedTaskId, lat, lng);
        return saved;
    }

    @Transactional
    public ResponderLocation stopLocationSharing(Long taskId, String responderEmail) {
        User user = userRepository.findByEmail(responderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Responder not found: " + responderEmail));

        Long resolvedTaskId = validateAndResolveTask(taskId, responderEmail, user.getRole());

        try {
            trackingService.stopTracking(resolvedTaskId, responderEmail);
        } catch (Exception e) {
            log.warn("TrackingSession stop note: {}", e.getMessage());
        }

        // Fetch latest location to record stop status or save terminal record
        Optional<ResponderLocation> latestOpt = locationRepository.findTopByResponderEmailOrderByRecordedAtDesc(responderEmail);
        Double lat = 0.0;
        Double lng = 0.0;
        Double acc = 0.0;

        if (latestOpt.isPresent()) {
            lat = latestOpt.get().getLatitude();
            lng = latestOpt.get().getLongitude();
            acc = latestOpt.get().getAccuracy();
        }

        ResponderLocation stoppedLoc = new ResponderLocation(
            user.getId(),
            null,
            resolvedTaskId,
            responderEmail,
            lat,
            lng,
            acc,
            0.0,
            0.0,
            "STOPPED"
        );

        ResponderLocation saved = locationRepository.save(stoppedLoc);
        log.info("Responder {} stopped live GPS sharing for task #{}", responderEmail, resolvedTaskId);
        return saved;
    }

    public Map<String, Object> getLatestLocation(String responderEmail) {
        Optional<ResponderLocation> latestOpt = locationRepository.findTopByResponderEmailOrderByRecordedAtDesc(responderEmail);
        Map<String, Object> res = new HashMap<>();

        if (latestOpt.isEmpty()) {
            res.put("status", "none");
            res.put("sharingStatus", "STOPPED");
            res.put("message", "Live location sharing is stopped.");
            return res;
        }

        ResponderLocation loc = latestOpt.get();
        boolean isStale = loc.getRecordedAt().isBefore(LocalDateTime.now().minusSeconds(60));

        res.put("status", "success");
        res.put("id", loc.getId());
        res.put("responderId", loc.getResponderId());
        res.put("responderEmail", loc.getResponderEmail());
        res.put("taskId", loc.getAssignmentId());
        res.put("latitude", loc.getLatitude());
        res.put("longitude", loc.getLongitude());
        res.put("accuracy", loc.getAccuracy());
        res.put("heading", loc.getHeading());
        res.put("speed", loc.getSpeed());
        res.put("sharingStatus", loc.getSharingStatus());
        res.put("recordedAt", loc.getRecordedAt().toString());
        res.put("isStale", isStale);

        if ("STOPPED".equalsIgnoreCase(loc.getSharingStatus())) {
            res.put("displayNotice", "Live location sharing is stopped.");
        } else if (isStale) {
            res.put("displayNotice", "Location update is outdated.");
        } else {
            res.put("displayNotice", "Live GPS actively broadcasting.");
        }

        return res;
    }

    private void validateCoordinates(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Latitude and longitude must not be null.");
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0 degrees.");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0 degrees.");
        }
    }

    private Long validateAndResolveTask(Long taskId, String responderEmail, String role) {
        if (taskId == null) {
            // If no specific task was specified, check if responder has an active in-progress task
            Optional<RescueTask> inProgress = taskRepository.findTopByAssignedToAndStatusNotOrderByCreatedAtDesc(
                responderEmail, "Completed"
            );
            if (inProgress.isPresent()) {
                return inProgress.get().getTaskId();
            }
            throw new IllegalArgumentException("No task ID provided and no active assigned tasks found for " + responderEmail);
        }

        RescueTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Rescue task #" + taskId + " not found."));

        // Verify responder is assigned to this task (or has Admin override role)
        boolean isAssigned = task.getAssignedTo() != null && task.getAssignedTo().equalsIgnoreCase(responderEmail);
        boolean isAdmin = role != null && role.equalsIgnoreCase("Admin");

        if (!isAssigned && !isAdmin) {
            throw new SecurityException("Unauthorized: Responder " + responderEmail + " is not assigned to task #" + taskId);
        }

        return task.getTaskId();
    }
}

