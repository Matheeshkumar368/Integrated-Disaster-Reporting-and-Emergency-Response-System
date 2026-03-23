package com.disaster.management.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.repository.CitizenReportRepository;

@Service
public class CitizenReportService {

    @Autowired
    private CitizenReportRepository reportRepository;

    public CitizenReport createReport(CitizenReport report) {
        return reportRepository.save(report);
    }

    public List<CitizenReport> getAllReports() {
        return reportRepository.findAllByOrderByTimestampDesc();
    }

    public List<CitizenReport> getPendingReports() {
        return reportRepository.findByStatusOrderByTimestampDesc("Pending");
    }

    public CitizenReport acknowledgeReport(Long reportId) {
        CitizenReport report = reportRepository.findById(reportId).orElse(null);
        if (report != null) {
            report.setStatus("Acknowledged");
            return reportRepository.save(report);
        }
        return null;
    }

    public CitizenReport resolveReport(Long reportId) {
        CitizenReport report = reportRepository.findById(reportId).orElse(null);
        if (report != null) {
            report.setStatus("Resolved");
            return reportRepository.save(report);
        }
        return null;
    }
}
