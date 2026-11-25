package com.disaster.management.event;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.RescueTask;

public class ResponderAssignedEvent {
    private final CitizenReport report;
    private final RescueTask task;
    private final String responderEmail;
    private final String responderName;
    private final String assignedBy;

    public ResponderAssignedEvent(CitizenReport report, RescueTask task, String responderEmail, String responderName, String assignedBy) {
        this.report = report;
        this.task = task;
        this.responderEmail = responderEmail;
        this.responderName = responderName;
        this.assignedBy = assignedBy;
    }

    public CitizenReport getReport() {
        return report;
    }

    public RescueTask getTask() {
        return task;
    }

    public String getResponderEmail() {
        return responderEmail;
    }

    public String getResponderName() {
        return responderName;
    }

    public String getAssignedBy() {
        return assignedBy;
    }
}
