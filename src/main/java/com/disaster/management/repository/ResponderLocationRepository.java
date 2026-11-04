package com.disaster.management.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.ResponderLocation;

@Repository
public interface ResponderLocationRepository extends JpaRepository<ResponderLocation, Long> {
    List<ResponderLocation> findBySessionIdOrderByRecordedAtAsc(Long sessionId);
    List<ResponderLocation> findByAssignmentIdOrderByRecordedAtAsc(Long assignmentId);
    List<ResponderLocation> findByAssignmentIdOrderByRecordedAtDesc(Long assignmentId);
    List<ResponderLocation> findByResponderEmailAndRecordedAtAfterOrderByRecordedAtAsc(String responderEmail, LocalDateTime after);
    Optional<ResponderLocation> findTopByResponderEmailOrderByRecordedAtDesc(String responderEmail);
    Optional<ResponderLocation> findTopByAssignmentIdOrderByRecordedAtDesc(Long assignmentId);
    List<ResponderLocation> findByResponderEmailOrderByRecordedAtDesc(String responderEmail);
    List<ResponderLocation> findBySharingStatus(String sharingStatus);
}

