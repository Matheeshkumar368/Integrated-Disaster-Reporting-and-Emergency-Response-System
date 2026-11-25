package com.disaster.management.event;

import com.disaster.management.entity.CitizenReport;

public class ReportVerifiedEvent {
    private final CitizenReport report;
    private final String adminEmail;

    public ReportVerifiedEvent(CitizenReport report, String adminEmail) {
        this.report = report;
        this.adminEmail = adminEmail;
    }

    public CitizenReport getReport() {
        return report;
    }

    public String getAdminEmail() {
        return adminEmail;
    }
}
