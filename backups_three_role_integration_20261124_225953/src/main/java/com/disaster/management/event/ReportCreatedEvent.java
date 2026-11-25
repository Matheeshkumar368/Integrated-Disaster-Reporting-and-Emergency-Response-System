package com.disaster.management.event;

import com.disaster.management.entity.CitizenReport;

public class ReportCreatedEvent {
    private final CitizenReport report;

    public ReportCreatedEvent(CitizenReport report) {
        this.report = report;
    }

    public CitizenReport getReport() {
        return report;
    }
}
