package com.disaster.management.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.ResponderLocation;
import com.disaster.management.entity.TrackingSession;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.ResponderLocationRepository;
import com.disaster.management.repository.TrackingSessionRepository;

@Service
public class ResponderTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ResponderTrackingService.class);

    // assignmentId -> active SseEmitters
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByAssignment = new ConcurrentHashMap<>();

    // assignmentId -> latest active TrackingSession cache
    private final Map<Long, TrackingSession> activeSessionsCache = new ConcurrentHashMap<>();

    @Autowired
    private TrackingSessionRepository sessionRepository;

    @Autowired
    private ResponderLocationRepository locationRepository;

    @Autowired
    private RescueTaskRepository taskRepository;

    @Autowired
    private RouteEstimationService routeService;

    @Autowired
    private IncidentTimelineService timelineService;

    @Transactional
    public TrackingSession startTracking(
            Long assignmentId,
            String responderEmail,
            String responderName,
            Double currentLat,
            Double currentLng,
            Double accuracy) {

        Optional<RescueTask> taskOpt = taskRepository.findById(assignmentId);
        Double destLat = null;
        Double destLng = null;
        String destAddress = null;

        if (taskOpt.isPresent()) {
            RescueTask task = taskOpt.get();
            destLat = task.getLatitude();
            destLng = task.getLongitude();
            destAddress = task.getLocationName() != null ? task.getLocationName() : task.getZone();

            // Auto-update task status to In Progress if currently Pending
            if ("Pending".equalsIgnoreCase(task.getStatus())) {
                task.setStatus("In Progress");
                taskRepository.save(task);
            }
        }

        // Close any prior active session for this assignment
        Optional<TrackingSession> existingActive = sessionRepository
                .findTopByAssignmentIdAndStatusOrderByStartedAtDesc(assignmentId, "ACTIVE");
        if (existingActive.isPresent()) {
            TrackingSession old = existingActive.get();
            old.setStatus("COMPLETED");
            old.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(old);
        }

        TrackingSession session = new TrackingSession();
        session.setAssignmentId(assignmentId);
        session.setResponderEmail(responderEmail);
        session.setResponderName(responderName);
        session.setStatus("ACTIVE");
        session.setDestinationLatitude(destLat);
        session.setDestinationLongitude(destLng);
        session.setDestinationAddress(destAddress);
        session.setCurrentLatitude(currentLat);
        session.setCurrentLongitude(currentLng);
        session.setCurrentAccuracy(accuracy);
        session.setLastHeartbeatAt(LocalDateTime.now());
        session.setStartedAt(LocalDateTime.now());

        // Calculate initial route & ETA if destination coordinates exist
        if (destLat != null && destLng != null && currentLat != null && currentLng != null) {
            Map<String, Object> route = routeService.calculateRoute(currentLat, currentLng, destLat, destLng);
            if ("success".equals(route.get("status"))) {
                Number distNum = (Number) route.get("distanceMeters");
                Number durNum = (Number) route.get("durationSeconds");
                if (distNum != null) session.setEstimatedDistanceMeters(distNum.doubleValue());
                if (durNum != null) session.setEstimatedDurationSeconds(durNum.intValue());
            }
        }

        TrackingSession savedSession = sessionRepository.save(session);
        activeSessionsCache.put(assignmentId, savedSession);

        // Record initial breadcrumb
        if (currentLat != null && currentLng != null) {
            ResponderLocation loc = new ResponderLocation(
                savedSession.getId(), assignmentId, responderEmail,
                currentLat, currentLng, accuracy, 0.0, 0.0
            );
            locationRepository.save(loc);
        }

        // Log timeline milestone
        timelineService.logEvent(
            assignmentId,
            "RESCUE_TASK",
            "EN_ROUTE",
            responderEmail,
            "Responder",
            responderName,
            (responderName != null ? responderName : responderEmail) + " started live location sharing and is EN ROUTE.",
            null,
            "{\"lat\":" + currentLat + ",\"lng\":" + currentLng + "}"
        );

        broadcastTrackingPayload(assignmentId, buildTelemetryPayload(savedSession, null));
        log.info("Tracking session #{} started for assignment #{} by {}", savedSession.getId(), assignmentId, responderEmail);
        return savedSession;
    }

    @Transactional
    public TrackingSession updateLocation(
            Long assignmentId,
            String responderEmail,
            Double latitude,
            Double longitude,
            Double accuracy,
            Double heading,
            Double speed) {

        TrackingSession session = activeSessionsCache.get(assignmentId);
        if (session == null) {
            Optional<TrackingSession> sessionOpt = sessionRepository
                    .findTopByAssignmentIdAndStatusOrderByStartedAtDesc(assignmentId, "ACTIVE");
            if (sessionOpt.isPresent()) {
                session = sessionOpt.get();
            } else {
                // If not found, create a new tracking session on the fly
                session = startTracking(assignmentId, responderEmail, null, latitude, longitude, accuracy);
            }
        }

        session.setCurrentLatitude(latitude);
        session.setCurrentLongitude(longitude);
        session.setCurrentAccuracy(accuracy);
        session.setCurrentHeading(heading);
        session.setCurrentSpeed(speed);
        session.setLastHeartbeatAt(LocalDateTime.now());

        Map<String, Object> routeInfo = null;
        if (session.getDestinationLatitude() != null && session.getDestinationLongitude() != null) {
            routeInfo = routeService.calculateRoute(
                latitude, longitude,
                session.getDestinationLatitude(), session.getDestinationLongitude()
            );
            if ("success".equals(routeInfo.get("status"))) {
                Number distNum = (Number) routeInfo.get("distanceMeters");
                Number durNum = (Number) routeInfo.get("durationSeconds");
                if (distNum != null) session.setEstimatedDistanceMeters(distNum.doubleValue());
                if (durNum != null) session.setEstimatedDurationSeconds(durNum.intValue());
            }
        }

        TrackingSession saved = sessionRepository.save(session);
        activeSessionsCache.put(assignmentId, saved);

        // Record breadcrumb
        ResponderLocation breadcrumb = new ResponderLocation(
            saved.getId(), assignmentId, responderEmail,
            latitude, longitude, accuracy, heading, speed
        );
        locationRepository.save(breadcrumb);

        // Broadcast to all active SSE subscribers
        broadcastTrackingPayload(assignmentId, buildTelemetryPayload(saved, routeInfo));

        return saved;
    }

    @Transactional
    public TrackingSession stopTracking(Long assignmentId, String actorEmail) {
        TrackingSession session = activeSessionsCache.remove(assignmentId);
        if (session == null) {
            Optional<TrackingSession> sessionOpt = sessionRepository
                    .findTopByAssignmentIdAndStatusOrderByStartedAtDesc(assignmentId, "ACTIVE");
            if (sessionOpt.isPresent()) {
                session = sessionOpt.get();
            }
        }

        if (session != null) {
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);

            Map<String, Object> stoppedPayload = new HashMap<>();
            stoppedPayload.put("assignmentId", assignmentId);
            stoppedPayload.put("status", "COMPLETED");
            stoppedPayload.put("message", "Responder tracking session completed.");

            broadcastTrackingPayload(assignmentId, stoppedPayload);
            closeAllEmitters(assignmentId);

            timelineService.logEvent(
                assignmentId,
                "RESCUE_TASK",
                "ON_SCENE",
                actorEmail,
                "Responder",
                session.getResponderName(),
                "Responder tracking concluded (arrived on scene / mission completed).",
                null,
                null
            );
        }

        return session;
    }

    public SseEmitter subscribe(Long assignmentId) {
        Long resolvedId = assignmentId;
        if (!activeSessionsCache.containsKey(assignmentId) && assignmentId != null) {
            List<RescueTask> linked = taskRepository.findByCitizenReportId(assignmentId);
            if (!linked.isEmpty()) {
                resolvedId = linked.get(0).getTaskId();
            }
        }
        final Long targetId = resolvedId;

        // 5 minute timeout per SSE connection
        SseEmitter emitter = new SseEmitter(300_000L);

        emittersByAssignment.computeIfAbsent(targetId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(targetId, emitter));
        emitter.onTimeout(() -> removeEmitter(targetId, emitter));
        emitter.onError(e -> removeEmitter(targetId, emitter));

        // Immediately send current location if available
        TrackingSession current = activeSessionsCache.get(targetId);
        if (current == null) {
            current = sessionRepository.findTopByAssignmentIdOrderByStartedAtDesc(targetId).orElse(null);
        }

        try {
            if (current != null) {
                Map<String, Object> route = null;
                if (current.getDestinationLatitude() != null && current.getDestinationLongitude() != null
                        && current.getCurrentLatitude() != null && current.getCurrentLongitude() != null) {
                    route = routeService.calculateRoute(
                        current.getCurrentLatitude(), current.getCurrentLongitude(),
                        current.getDestinationLatitude(), current.getDestinationLongitude()
                    );
                }
                emitter.send(SseEmitter.event()
                    .name("tracking-update")
                    .data(buildTelemetryPayload(current, route)));
            } else {
                emitter.send(SseEmitter.event()
                    .name("tracking-init")
                    .data(Map.of("assignmentId", assignmentId, "status", "WAITING_FOR_SIGNAL")));
            }
        } catch (IOException e) {
            emitter.complete();
            removeEmitter(targetId, emitter);
        }

        return emitter;
    }

    public Map<String, Object> getLatestLocation(Long assignmentId) {
        TrackingSession session = activeSessionsCache.get(assignmentId);
        if (session == null) {
            session = sessionRepository.findTopByAssignmentIdOrderByStartedAtDesc(assignmentId).orElse(null);
        }

        if (session == null && assignmentId != null) {
            List<RescueTask> linkedTasks = taskRepository.findByCitizenReportId(assignmentId);
            if (!linkedTasks.isEmpty()) {
                Long linkedTaskId = linkedTasks.get(0).getTaskId();
                session = activeSessionsCache.get(linkedTaskId);
                if (session == null) {
                    session = sessionRepository.findTopByAssignmentIdOrderByStartedAtDesc(linkedTaskId).orElse(null);
                }
            }
        }

        if (session == null) {
            return Map.of("assignmentId", assignmentId, "status", "NO_SESSION");
        }

        Map<String, Object> route = null;
        if (session.getDestinationLatitude() != null && session.getDestinationLongitude() != null
                && session.getCurrentLatitude() != null && session.getCurrentLongitude() != null) {
            route = routeService.calculateRoute(
                session.getCurrentLatitude(), session.getCurrentLongitude(),
                session.getDestinationLatitude(), session.getDestinationLongitude()
            );
        }

        return buildTelemetryPayload(session, route);
    }

    public List<Map<String, Object>> getActiveSessionsForRadar() {
        List<TrackingSession> sessions = sessionRepository.findByStatus("ACTIVE");
        List<Map<String, Object>> list = new ArrayList<>();
        for (TrackingSession s : sessions) {
            list.add(buildTelemetryPayload(s, null));
        }
        return list;
    }

    private Map<String, Object> buildTelemetryPayload(TrackingSession session, Map<String, Object> routeInfo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("assignmentId", session.getAssignmentId());
        payload.put("responderEmail", session.getResponderEmail());
        payload.put("responderName", session.getResponderName());
        payload.put("status", session.getStatus());
        payload.put("latitude", session.getCurrentLatitude());
        payload.put("longitude", session.getCurrentLongitude());
        payload.put("accuracy", session.getCurrentAccuracy());
        payload.put("heading", session.getCurrentHeading());
        payload.put("speed", session.getCurrentSpeed());
        payload.put("destinationLatitude", session.getDestinationLatitude());
        payload.put("destinationLongitude", session.getDestinationLongitude());
        payload.put("destinationAddress", session.getDestinationAddress());
        payload.put("lastHeartbeatAt", session.getLastHeartbeatAt());
        payload.put("startedAt", session.getStartedAt());

        // Check if signal is stale (> 60 seconds without heartbeat)
        boolean isStale = false;
        if (session.getLastHeartbeatAt() != null) {
            isStale = session.getLastHeartbeatAt().isBefore(LocalDateTime.now().minusSeconds(60));
        }
        payload.put("isStale", isStale);

        if (routeInfo != null && "success".equals(routeInfo.get("status"))) {
            payload.put("distanceMeters", routeInfo.get("distanceMeters"));
            payload.put("distanceKm", routeInfo.get("distanceKm"));
            payload.put("durationSeconds", routeInfo.get("durationSeconds"));
            payload.put("durationMinutes", routeInfo.get("durationMinutes"));
            payload.put("etaText", routeInfo.get("etaText"));
            payload.put("routeSource", routeInfo.get("source"));
            payload.put("routeCoordinates", routeInfo.get("coordinates"));
        } else {
            payload.put("distanceMeters", session.getEstimatedDistanceMeters());
            payload.put("durationSeconds", session.getEstimatedDurationSeconds());
            if (session.getEstimatedDurationSeconds() != null) {
                int mins = Math.max(1, (int) Math.ceil(session.getEstimatedDurationSeconds() / 60.0));
                payload.put("etaText", mins + " mins remaining");
            }
        }

        return payload;
    }

    private void broadcastTrackingPayload(Long assignmentId, Map<String, Object> payload) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByAssignment.get(assignmentId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> failedEmitters = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                    .name("tracking-update")
                    .data(payload));
            } catch (Exception e) {
                failedEmitters.add(emitter);
            }
        }

        list.removeAll(failedEmitters);
    }

    private void removeEmitter(Long assignmentId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByAssignment.get(assignmentId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByAssignment.remove(assignmentId);
            }
        }
    }

    private void closeAllEmitters(Long assignmentId) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByAssignment.remove(assignmentId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }
}
