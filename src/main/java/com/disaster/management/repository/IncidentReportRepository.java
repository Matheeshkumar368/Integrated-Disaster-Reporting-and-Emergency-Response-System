package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.IncidentReport;

@Repository
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {
    
    // Find reports by reporter
    List<IncidentReport> findByReportedBy(String reportedBy);
    
    // Find reports by zone
    List<IncidentReport> findByZone(String zone);
    
    // Find reports by status
    List<IncidentReport> findByStatus(String status);
    
    // Find reports by report type
    List<IncidentReport> findByReportType(String reportType);
    
    // Find reports by reporter and status
    List<IncidentReport> findByReportedByAndStatus(String reportedBy, String status);
    
    // Find all ordered by timestamp descending
    List<IncidentReport> findAllByOrderByTimestampDesc();
    
    // Find by zone and status
    List<IncidentReport> findByZoneAndStatus(String zone, String status);
}
