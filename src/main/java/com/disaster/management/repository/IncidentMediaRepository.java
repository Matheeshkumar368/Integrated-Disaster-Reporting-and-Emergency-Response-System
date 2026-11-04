package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.IncidentMedia;

@Repository
public interface IncidentMediaRepository extends JpaRepository<IncidentMedia, Long> {
    List<IncidentMedia> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);
    List<IncidentMedia> findByIncidentIdAndIncidentTypeOrderByCreatedAtDesc(Long incidentId, String incidentType);
    List<IncidentMedia> findByUploaderEmailOrderByCreatedAtDesc(String uploaderEmail);
    List<IncidentMedia> findTop50ByOrderByCreatedAtDesc();
}

