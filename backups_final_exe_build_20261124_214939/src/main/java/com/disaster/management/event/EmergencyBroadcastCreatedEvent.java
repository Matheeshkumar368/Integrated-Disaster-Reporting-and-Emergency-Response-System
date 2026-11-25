package com.disaster.management.event;

import com.disaster.management.entity.Alert;

public class EmergencyBroadcastCreatedEvent {
    private final Alert alert;
    private final String broadcastedBy;

    public EmergencyBroadcastCreatedEvent(Alert alert, String broadcastedBy) {
        this.alert = alert;
        this.broadcastedBy = broadcastedBy;
    }

    public Alert getAlert() {
        return alert;
    }

    public String getBroadcastedBy() {
        return broadcastedBy;
    }
}
