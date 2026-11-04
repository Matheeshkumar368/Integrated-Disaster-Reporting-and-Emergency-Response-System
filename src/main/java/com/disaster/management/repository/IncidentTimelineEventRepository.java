package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.IncidentTimelineEvent;

@Repository
public interface IncidentTimelineEventRepository extends JpaRepository<IncidentTimelineEvent, Long> {
    List<IncidentTimelineEvent> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
    List<IncidentTimelineEvent> findByIncidentIdAndIncidentTypeOrderByCreatedAtAsc(Long incidentId, String incidentType);
}

