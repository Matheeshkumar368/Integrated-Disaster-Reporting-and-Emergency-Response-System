package com.disaster.management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.TrackingSession;

@Repository
public interface TrackingSessionRepository extends JpaRepository<TrackingSession, Long> {
    Optional<TrackingSession> findTopByAssignmentIdAndStatusOrderByStartedAtDesc(Long assignmentId, String status);
    Optional<TrackingSession> findTopByAssignmentIdOrderByStartedAtDesc(Long assignmentId);
    List<TrackingSession> findByResponderEmailAndStatus(String responderEmail, String status);
    List<TrackingSession> findByStatus(String status);
}

