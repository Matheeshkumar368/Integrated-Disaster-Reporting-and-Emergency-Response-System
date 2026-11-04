package com.disaster.management.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.OfficialAlert;

@Repository
public interface OfficialAlertRepository extends JpaRepository<OfficialAlert, Long> {

    Optional<OfficialAlert> findBySourceAlertId(String sourceAlertId);

    boolean existsBySourceAlertId(String sourceAlertId);

    List<OfficialAlert> findByIsExpiredFalseOrderByPublishedTimeDesc();

    List<OfficialAlert> findByIsExpiredFalseOrderByEffectiveTimeDesc();

    List<OfficialAlert> findAllByOrderByPublishedTimeDesc();

    List<OfficialAlert> findBySeverityAndIsExpiredFalse(String severity);

    List<OfficialAlert> findByHazardTypeIgnoreCaseAndIsExpiredFalse(String hazardType);

    long countByIsExpiredFalse();

    @Modifying
    @Transactional
    @Query("UPDATE OfficialAlert a SET a.isExpired = true, a.updatedAt = :now WHERE a.expiresTime IS NOT NULL AND a.expiresTime < :now AND a.isExpired = false")
    int markExpiredAlerts(@Param("now") LocalDateTime now);
}

