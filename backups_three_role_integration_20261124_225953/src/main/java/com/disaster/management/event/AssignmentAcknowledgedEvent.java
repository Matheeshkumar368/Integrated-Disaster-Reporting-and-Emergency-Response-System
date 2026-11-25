package com.disaster.management.event;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.RescueTask;

public class AssignmentAcknowledgedEvent {
    private final RescueTask task;
    private final CitizenReport report;
    private final String responderEmail;

    public AssignmentAcknowledgedEvent(RescueTask task, CitizenReport report, String responderEmail) {
        this.task = task;
        this.report = report;
        this.responderEmail = responderEmail;
    }

    public RescueTask getTask() {
        return task;
    }

    public CitizenReport getReport() {
        return report;
    }

    public String getResponderEmail() {
        return responderEmail;
    }
}
