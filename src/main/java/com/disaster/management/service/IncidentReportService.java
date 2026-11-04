package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.IncidentReport;
import com.disaster.management.repository.IncidentReportRepository;

@Service
public class IncidentReportService {

    @Autowired
    private IncidentReportRepository incidentReportRepository;

    // Create a new incident report
    @Transactional
    public IncidentReport createReport(IncidentReport report) {
        if (report.getTitle() == null || report.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Report title is required");
        }
        if (report.getDescription() == null || report.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Report description is required");
        }
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
    @Transactional
    public IncidentReport updateReportStatus(Long reportId, String status) {
        if (reportId == null || status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Report ID and status are required");
        }
        Optional<IncidentReport> reportOpt = incidentReportRepository.findById(reportId);
        if (reportOpt.isPresent()) {
            IncidentReport report = reportOpt.get();
            report.setStatus(status);
            return incidentReportRepository.save(report);
        }
        return null;
    }

    // Update report
    @Transactional
    public IncidentReport updateReport(IncidentReport report) {
        if (report == null || report.getReportId() == null) {
            throw new IllegalArgumentException("Report is required");
        }
        return incidentReportRepository.save(report);
    }

    // Delete report
    @Transactional
    public void deleteReport(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Report ID is required");
        }
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
