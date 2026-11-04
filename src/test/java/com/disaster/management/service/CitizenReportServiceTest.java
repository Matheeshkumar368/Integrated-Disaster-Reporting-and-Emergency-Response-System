package com.disaster.management.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.repository.CitizenReportRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitizenReportServiceTest {

    @Mock
    private CitizenReportRepository reportRepository;

    @InjectMocks
    private CitizenReportService reportService;

    private CitizenReport report;

    @BeforeEach
    void setUp() {
        report = new CitizenReport();
        report.setReportId(101L);
        report.setCitizenEmail("citizen@gmail.com");
        report.setLocation("Chennai - T Nagar");
        report.setMessage("Severe street waterlogging");
        report.setStatus("Pending");
    }

    @Test
    @DisplayName("Create citizen report successfully with defaults")
    void testCreateReport() {
        when(reportRepository.save(any(CitizenReport.class))).thenReturn(report);

        CitizenReport saved = reportService.createReport(report);

        assertNotNull(saved);
        assertEquals("Pending", saved.getStatus());
        assertEquals("citizen@gmail.com", saved.getCitizenEmail());
        verify(reportRepository).save(report);
    }

    @Test
    @DisplayName("Validation fails when report message is missing")
    void testCreateReportValidation() {
        CitizenReport invalidReport = new CitizenReport();
        invalidReport.setLocation("Madurai");
        invalidReport.setMessage("");

        assertThrows(IllegalArgumentException.class, () -> reportService.createReport(invalidReport));
    }

    @Test
    @DisplayName("Acknowledge report transitions status to Acknowledged")
    void testAcknowledgeReport() {
        when(reportRepository.findById(101L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(CitizenReport.class))).thenAnswer(i -> i.getArgument(0));

        CitizenReport acknowledged = reportService.acknowledgeReport(101L);

        assertNotNull(acknowledged);
        assertEquals("Acknowledged", acknowledged.getStatus());
        verify(reportRepository).save(report);
    }

    @Test
    @DisplayName("Resolve report transitions status to Resolved")
    void testResolveReport() {
        when(reportRepository.findById(101L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(CitizenReport.class))).thenAnswer(i -> i.getArgument(0));

        CitizenReport resolved = reportService.resolveReport(101L);

        assertNotNull(resolved);
        assertEquals("Resolved", resolved.getStatus());
        verify(reportRepository).save(report);
    }

    @Test
    @DisplayName("Retrieve personal citizen reports by email")
    void testGetReportsByCitizen() {
        when(reportRepository.findByCitizenEmailAndIsDeletedFalseOrderByTimestampDesc("citizen@gmail.com"))
            .thenReturn(List.of(report));

        List<CitizenReport> reports = reportService.getReportsByCitizen("citizen@gmail.com");

        assertEquals(1, reports.size());
        assertEquals("citizen@gmail.com", reports.get(0).getCitizenEmail());
    }
}

