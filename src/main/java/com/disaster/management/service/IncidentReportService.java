package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.disaster.management.entity.IncidentReport;
import com.disaster.management.repository.IncidentReportRepository;

@Service
public class IncidentReportService {

    @Autowired
    private IncidentReportRepository incidentReportRepository;

    // Create a new incident report
    public IncidentReport createReport(IncidentReport report) {
        report.setTimestamp(LocalDateTime.now());
        report.setStatus("Submitted");
        return incidentReportRepository.save(report);
    }

    // Get all reports
    public List<IncidentReport> getAllReports() {
        return incidentReportRepository.findAllByOrderByTimestampDesc();
    }

    // Get report by ID
    public Optional<IncidentReport> getReportById(Long id) {
        return incidentReportRepository.findById(id);
    }

    // Get reports by reporter
    public List<IncidentReport> getReportsByReporter(String email) {
        return incidentReportRepository.findByReportedBy(email);
    }

    // Get reports by zone
    public List<IncidentReport> getReportsByZone(String zone) {
        return incidentReportRepository.findByZone(zone);
    }

    // Get reports by status
    public List<IncidentReport> getReportsByStatus(String status) {
        return incidentReportRepository.findByStatus(status);
    }

    // Get reports by type
    public List<IncidentReport> getReportsByType(String type) {
        return incidentReportRepository.findByReportType(type);
    }

    // Update report status
    public IncidentReport updateReportStatus(Long reportId, String status) {
        Optional<IncidentReport> reportOpt = incidentReportRepository.findById(reportId);
        if (reportOpt.isPresent()) {
            IncidentReport report = reportOpt.get();
            report.setStatus(status);
            return incidentReportRepository.save(report);
        }
        return null;
    }

    // Update report
    public IncidentReport updateReport(IncidentReport report) {
        return incidentReportRepository.save(report);
    }

    // Delete report
    public void deleteReport(Long id) {
        incidentReportRepository.deleteById(id);
    }

    // Get reports by reporter and status
    public List<IncidentReport> getReportsByReporterAndStatus(String email, String status) {
        return incidentReportRepository.findByReportedByAndStatus(email, status);
    }

    // Get reports by zone and status
    public List<IncidentReport> getReportsByZoneAndStatus(String zone, String status) {
        return incidentReportRepository.findByZoneAndStatus(zone, status);
    }
}
