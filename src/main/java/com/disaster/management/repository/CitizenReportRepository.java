package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.CitizenReport;

@Repository
public interface CitizenReportRepository extends JpaRepository<CitizenReport, Long> {

    List<CitizenReport> findByStatusOrderByTimestampDesc(String status);

    List<CitizenReport> findAllByOrderByTimestampDesc();

    List<CitizenReport> findByCitizenEmailOrderByTimestampDesc(String citizenEmail);

    List<CitizenReport> findByIsDeletedFalseOrderByTimestampDesc();

    List<CitizenReport> findByCitizenEmailAndIsDeletedFalseOrderByTimestampDesc(String citizenEmail);

    List<CitizenReport> findByIsArchivedTrueAndIsDeletedFalseOrderByTimestampDesc();

    List<CitizenReport> findByLiveTrackingEnabledTrueAndIsDeletedFalse();

    long countByStatus(String status);

    long countByIsDeletedFalse();

    @Query("SELECT r FROM CitizenReport r WHERE r.isDeleted = false " +
           "AND (:status IS NULL OR :status = '' OR LOWER(r.status) = LOWER(:status)) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(r.message) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(r.location) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(r.citizenEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(r.incidentType) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY r.timestamp DESC")
    Page<CitizenReport> searchReports(
        @Param("search") String search,
        @Param("status") String status,
        Pageable pageable
    );
}
