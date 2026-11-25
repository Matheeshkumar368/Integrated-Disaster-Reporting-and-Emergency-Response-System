package com.disaster.management.event;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.RescueTask;

public class MissionStatusChangedEvent {
    private final RescueTask task;
    private final CitizenReport report;
    private final String newStatus;
    private final String notes;
    private final String actorEmail;

    public MissionStatusChangedEvent(RescueTask task, CitizenReport report, String newStatus, String notes, String actorEmail) {
        this.task = task;
        this.report = report;
        this.newStatus = newStatus;
        this.notes = notes;
        this.actorEmail = actorEmail;
    }

    public RescueTask getTask() {
        return task;
    }

    public CitizenReport getReport() {
        return report;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getNotes() {
        return notes;
    }

    public String getActorEmail() {
        return actorEmail;
    }
}
