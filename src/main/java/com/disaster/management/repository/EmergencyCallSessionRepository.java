package com.disaster.management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.EmergencyCallSession;

@Repository
public interface EmergencyCallSessionRepository extends JpaRepository<EmergencyCallSession, Long> {
    Optional<EmergencyCallSession> findByCallId(String callId);
    Optional<EmergencyCallSession> findTopByCitizenEmailAndStatusOrderByStartedAtDesc(String citizenEmail, String status);
    Optional<EmergencyCallSession> findTopByCitizenEmailIgnoreCaseAndStatusOrderByStartedAtDesc(String citizenEmail, String status);
    List<EmergencyCallSession> findByIncidentIdOrderByStartedAtDesc(Long incidentId);
    List<EmergencyCallSession> findByResponderEmailOrderByStartedAtDesc(String responderEmail);
}
