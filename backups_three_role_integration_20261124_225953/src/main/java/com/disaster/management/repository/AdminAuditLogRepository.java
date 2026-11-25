package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.AdminAuditLog;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findAllByOrderByTimestampDesc();
    List<AdminAuditLog> findTop50ByOrderByTimestampDesc();
    List<AdminAuditLog> findByActorEmailOrderByTimestampDesc(String actorEmail);
}

