package com.disaster.management.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.disaster.management.entity.Alert;
import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.service.NotificationService;

/**
 * Central event listener for cross-role synchronized domain events.
 * Listens to domain lifecycle triggers and guarantees reliable notification persistence and auditability.
 */
@Component
public class DisasterEventListener {

    private static final Logger log = LoggerFactory.getLogger(DisasterEventListener.class);

    @Autowired(required = false)
    private NotificationService notificationService;

    @EventListener
    public void onReportCreated(ReportCreatedEvent event) {
        CitizenReport report = event.getReport();
        log.info("[EVENT] ReportCreatedEvent: Report #{} submitted by [{}] at {}",
            report.getReportId(), report.getCitizenEmail(), report.getLocation());

        if (notificationService != null) {
            try {
                notificationService.notifyAdmins(
                    "New Disaster Reported",
                    "Incident #" + report.getReportId() + " (" + report.getIncidentType() + ") reported in " + report.getLocation(),
                    "DISASTER",
                    report.getSeverity() != null ? report.getSeverity() : "High",
                    report.getReportId(),
                    "CITIZEN_REPORT",
                    "admin-notifications.html"
                );
                notificationService.notifyCitizen(
                    report.getCitizenEmail(),
                    "Report Received",
                    "Your incident report #" + report.getReportId() + " has been received and is being verified.",
                    "UPDATE",
                    "Medium",
                    report.getReportId(),
                    "CITIZEN_REPORT",
                    "citizen-notifications.html"
                );
            } catch (Exception e) {
                log.warn("[EVENT] Notification error on ReportCreatedEvent: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void onReportVerified(ReportVerifiedEvent event) {
        CitizenReport report = event.getReport();
        log.info("[EVENT] ReportVerifiedEvent: Report #{} verified by [{}]",
            report.getReportId(), event.getAdminEmail());

        if (notificationService != null) {
            try {
                notificationService.notifyCitizen(
                    report.getCitizenEmail(),
                    "Report Verified",
                    "Your incident report #" + report.getReportId() + " has been verified by command personnel.",
                    "UPDATES",
                    "High",
                    report.getReportId(),
                    "CITIZEN_REPORT",
                    "citizen-notifications.html"
                );
            } catch (Exception e) {
                log.warn("[EVENT] Notification error on ReportVerifiedEvent: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void onResponderAssigned(ResponderAssignedEvent event) {
        CitizenReport report = event.getReport();
        RescueTask task = event.getTask();
        log.info("[EVENT] ResponderAssignedEvent: Task #{} (Report #{}) assigned to [{}] ({}) by [{}]",
            task != null ? task.getTaskId() : "N/A",
            report != null ? report.getReportId() : "N/A",
            event.getResponderEmail(), event.getResponderName(), event.getAssignedBy());

        if (notificationService != null) {
            try {
                // Notify Responder
                notificationService.notifyResponder(
                    event.getResponderEmail(),
                    "New Rescue Assignment",
                    "You have been assigned to Incident #" + (report != null ? report.getReportId() : (task != null ? task.getTaskId() : "")) +
                    " in " + (report != null ? report.getLocation() : (task != null ? task.getZone() : "assigned area")),
                    "ASSIGNMENT",
                    (report != null && report.getSeverity() != null) ? report.getSeverity() : "High",
                    report != null ? report.getReportId() : (task != null ? task.getTaskId() : null),
                    report != null ? "CITIZEN_REPORT" : "TASK",
                    "responder-notifications.html"
                );

                // Notify Citizen
                if (report != null && report.getCitizenEmail() != null) {
                    notificationService.notifyCitizen(
                        report.getCitizenEmail(),
                        "Rescue Team Assigned",
                        "Rescue unit " + event.getResponderName() + " has been assigned to your report.",
                        "RESPONSE",
                        "High",
                        report.getReportId(),
                        "CITIZEN_REPORT",
                        "citizen-notifications.html"
                    );
                }
            } catch (Exception e) {
                log.warn("[EVENT] Notification error on ResponderAssignedEvent: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void onAssignmentAcknowledged(AssignmentAcknowledgedEvent event) {
        RescueTask task = event.getTask();
        CitizenReport report = event.getReport();
        log.info("[EVENT] AssignmentAcknowledgedEvent: Task #{} acknowledged by [{}]",
            task != null ? task.getTaskId() : "N/A", event.getResponderEmail());

        if (notificationService != null) {
            try {
                // Notify Admin
                notificationService.notifyAdmins(
                    "Task Acknowledged",
                    "Responder " + event.getResponderEmail() + " acknowledged Task #" + (task != null ? task.getTaskId() : ""),
                    "UPDATE",
                    "Medium",
                    task != null ? task.getTaskId() : null,
                    "TASK",
                    "admin-notifications.html"
                );

                // Notify Citizen
                if (report != null && report.getCitizenEmail() != null) {
                    notificationService.notifyCitizen(
                        report.getCitizenEmail(),
                        "Rescue Team Deployed",
                        "Responder " + (task != null && task.getAssignedResponderName() != null ? task.getAssignedResponderName() : event.getResponderEmail()) +
                        " has accepted task and is responding.",
                        "RESPONSE",
                        "High",
                        report.getReportId(),
                        "CITIZEN_REPORT",
                        "citizen-notifications.html"
                    );
                }
            } catch (Exception e) {
                log.warn("[EVENT] Notification error on AssignmentAcknowledgedEvent: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void onMissionStatusChanged(MissionStatusChangedEvent event) {
        RescueTask task = event.getTask();
        CitizenReport report = event.getReport();
        String status = event.getNewStatus();
        log.info("[EVENT] MissionStatusChangedEvent: Task #{} status changed to [{}] by [{}]",
            task != null ? task.getTaskId() : "N/A", status, event.getActorEmail());

        if (notificationService != null) {
            try {
                String responderDisplayName = (task != null && task.getAssignedResponderName() != null)
                    ? task.getAssignedResponderName() : (task != null ? task.getAssignedTo() : "Rescue Unit");

                // Route status-specific notifications
                if ("En Route".equalsIgnoreCase(status) || "On the Way".equalsIgnoreCase(status)) {
                    if (report != null && report.getCitizenEmail() != null) {
                        notificationService.notifyCitizen(
                            report.getCitizenEmail(), "Rescue Unit En Route",
                            "Rescue unit " + responderDisplayName + " is on the way to your location.",
                            "RESPONSE", "High", report.getReportId(), "CITIZEN_REPORT", "citizen-notifications.html"
                        );
                    }
                    notificationService.notifyAdmins(
                        "Unit En Route", "Responder " + responderDisplayName + " is en route to Task #" + (task != null ? task.getTaskId() : ""),
                        "UPDATE", "Medium", task != null ? task.getTaskId() : null, "TASK", "admin-notifications.html"
                    );
                } else if ("Arrived".equalsIgnoreCase(status) || "Reached Location".equalsIgnoreCase(status)) {
                    if (report != null && report.getCitizenEmail() != null) {
                        notificationService.notifyCitizen(
                            report.getCitizenEmail(), "Rescue Unit Arrived",
                            "Rescue unit " + responderDisplayName + " has arrived at your location.",
                            "RESPONSE", "High", report.getReportId(), "CITIZEN_REPORT", "citizen-notifications.html"
                        );
                    }
                    notificationService.notifyAdmins(
                        "Unit Arrived On Scene", "Responder " + responderDisplayName + " arrived on scene for Task #" + (task != null ? task.getTaskId() : ""),
                        "UPDATE", "Medium", task != null ? task.getTaskId() : null, "TASK", "admin-notifications.html"
                    );
                } else if ("Completed".equalsIgnoreCase(status) || "Resolved".equalsIgnoreCase(status)) {
                    if (report != null && report.getCitizenEmail() != null) {
                        notificationService.notifyCitizen(
                            report.getCitizenEmail(), "Mission Completed",
                            "Rescue operation for your report #" + report.getReportId() + " has completed successfully.",
                            "RESPONSE", "Low", report.getReportId(), "CITIZEN_REPORT", "citizen-notifications.html"
                        );
                    }
                    notificationService.notifyAdmins(
                        "Mission Completed", "Rescue Mission #" + (task != null ? task.getTaskId() : "") + " marked completed by " + responderDisplayName,
                        "UPDATE", "Medium", task != null ? task.getTaskId() : null, "TASK", "admin-notifications.html"
                    );
                }
            } catch (Exception e) {
                log.warn("[EVENT] Notification error on MissionStatusChangedEvent: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void onEmergencyBroadcastCreated(EmergencyBroadcastCreatedEvent event) {
        Alert alert = event.getAlert();
        log.info("[EVENT] EmergencyBroadcastCreatedEvent: Alert #{} ({}) broadcasted by [{}] in region [{}]",
            alert.getAlertId(), alert.getTitle(), event.getBroadcastedBy(), alert.getRegion());

        if (notificationService != null) {
            try {
                notificationService.notifyCitizensInRegion(
                    alert.getRegion(),
                    "Severe Disaster Alert: " + alert.getRegion(),
                    alert.getMessage(),
                    "ALERTS",
                    alert.getSeverity() != null ? alert.getSeverity() : "High",
                    alert.getAlertId(),
                    "ALERT",
                    "citizen-notifications.html"
                );
                notificationService.notifyRespondersInRegion(
                    alert.getRegion(),
                    "Emergency Alert Broadcast: " + alert.getRegion(),
                    alert.getMessage(),
                    "ALERTS",
                    alert.getSeverity() != null ? alert.getSeverity() : "High",
                    alert.getAlertId(),
                    "ALERT",
                    "responder-notifications.html"
                );
                notificationService.notifyAdmins(
                    "Emergency Alert Broadcasted: " + alert.getTitle(),
                    "Disaster alert created for region " + alert.getRegion() + ": " + alert.getMessage(),
                    "ALERTS",
                    alert.getSeverity() != null ? alert.getSeverity() : "High",
                    alert.getAlertId(),
                    "ALERT",
                    "admin-notifications.html"
                );
            } catch (Exception e) {
                log.warn("[EVENT] Notification error on EmergencyBroadcastCreatedEvent: {}", e.getMessage());
            }
        }
    }
}
