package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.IncidentTimelineEvent;
import com.disaster.management.repository.IncidentTimelineEventRepository;

@Service
public class IncidentTimelineService {

    private static final Logger log = LoggerFactory.getLogger(IncidentTimelineService.class);

    @Autowired
    private IncidentTimelineEventRepository timelineRepository;

    @Transactional
    public IncidentTimelineEvent logEvent(
            Long incidentId,
            String incidentType,
            String eventType,
            String actorEmail,
            String actorRole,
            String actorName,
            String message,
            Long mediaId,
            String payloadJson) {

        IncidentTimelineEvent event = new IncidentTimelineEvent(
            incidentId,
            incidentType != null ? incidentType : "CITIZEN_REPORT",
            eventType,
            actorEmail,
            actorRole,
            actorName != null ? actorName : actorEmail,
            message,
            mediaId,
            payloadJson
        );

        IncidentTimelineEvent saved = timelineRepository.save(event);
        log.debug("Logged timeline event [{}]: {} on incident #{}", eventType, message, incidentId);
        return saved;
    }

    public List<IncidentTimelineEvent> getTimeline(Long incidentId) {
        return timelineRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    public List<IncidentTimelineEvent> getTimeline(Long incidentId, String incidentType) {
        return timelineRepository.findByIncidentIdAndIncidentTypeOrderByCreatedAtAsc(incidentId, incidentType);
    }

    @Transactional
    public IncidentTimelineEvent postChatMessage(
            Long incidentId,
            String incidentType,
            String actorEmail,
            String actorRole,
            String actorName,
            String text) {

        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Message text cannot be empty.");
        }

        return logEvent(
            incidentId,
            incidentType,
            "MESSAGE",
            actorEmail,
            actorRole,
            actorName,
            text.trim(),
            null,
            null
        );
    }
}

