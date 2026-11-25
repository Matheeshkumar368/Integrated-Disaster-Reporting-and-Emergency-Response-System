package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.Alert;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.event.EmergencyBroadcastCreatedEvent;
import com.disaster.management.repository.AlertRepository;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private CitizenReportRepository citizenReportRepository;

    @Autowired
    private RescueTaskService rescueTaskService;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public Alert createAlertAndDispatchTasks(Alert alert, String adminEmail) {
        if (alert.getTitle() == null || alert.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert title is required");
        }
        if (alert.getMessage() == null || alert.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert message is required");
        }
        if (alert.getRegion() == null || alert.getRegion().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert region/zone is required");
        }

        // Duplicate active alert check: prevent duplicate active alerts for the same region and title
        List<Alert> duplicates = alertRepository.findByTitleIgnoreCaseAndRegionIgnoreCaseAndStatus(
            alert.getTitle().trim(), alert.getRegion().trim(), "Active"
        );
        if (!duplicates.isEmpty()) {
            log.warn("Duplicate active alert rejected for region [{}] with title [{}]", alert.getRegion(), alert.getTitle());
            throw new IllegalArgumentException("An active alert with title '" + alert.getTitle().trim() + "' already exists for region " + alert.getRegion().trim() + ". Please resolve or modify the existing active alert.");
        }

        alert.setCreatedBy(adminEmail);
        alert.setCreatedAt(LocalDateTime.now());
        if (alert.getStatus() == null || alert.getStatus().trim().isEmpty()) {
            alert.setStatus("Active");
        }
        alert.setBroadcasted(true);

        Alert savedAlert = alertRepository.save(alert);

        // Automatically assign rescue tasks to responders in the matching region
        try {
            Set<String> taskAssignedEmails = new HashSet<>();

            // 1. Zone-specific responders
            List<User> zoneResponders = userRepository.findByRoleAndAssignedZone("Responder", alert.getRegion());
            for (User responder : zoneResponders) {
                taskAssignedEmails.add(responder.getEmail());
                createTaskForResponder(savedAlert, responder, adminEmail);
            }

            // 2. Responders with general or unassigned zones
            List<User> generalResponders = userRepository.findByRole("Responder");
            for (User responder : generalResponders) {
                if (!taskAssignedEmails.contains(responder.getEmail()) &&
                    (responder.getAssignedZone() == null || responder.getAssignedZone().isEmpty() || "Unassigned".equalsIgnoreCase(responder.getAssignedZone()))) {
                    createTaskForResponder(savedAlert, responder, adminEmail);
                }
            }
        } catch (Exception e) {
            log.warn("Task auto-generation for alert encountered warning: {}", e.getMessage());
        }

        if (notificationService != null) {
            try {
                String mode = alert.getDeliveryMode();
                if (mode == null || mode.trim().isEmpty()) {
                    mode = "BROADCAST";
                }
                mode = mode.trim().toUpperCase();

                Set<String> recipientEmails = new java.util.LinkedHashSet<>();

                if ("INCIDENT".equals(mode)) {
                    // C. Incident-specific alert: Target Citizens associated with the relevant incident
                    if (alert.getRelatedIncidentId() != null && citizenReportRepository != null) {
                        citizenReportRepository.findById(alert.getRelatedIncidentId()).ifPresent(report -> {
                            if (report.getCitizenEmail() != null && !report.getCitizenEmail().isBlank()) {
                                userRepository.findByEmail(report.getCitizenEmail().trim().toLowerCase()).ifPresent(u -> {
                                    if ((u.getActive() == null || Boolean.TRUE.equals(u.getActive())) && "Citizen".equalsIgnoreCase(u.getRole())) {
                                        recipientEmails.add(u.getEmail().trim().toLowerCase());
                                    }
                                });
                            }
                        });
                    }
                    if (recipientEmails.isEmpty() && alert.getTargetRecipients() != null && !alert.getTargetRecipients().isBlank()) {
                        for (String item : alert.getTargetRecipients().split("[,;\\s]+")) {
                            String clean = item.trim().toLowerCase();
                            userRepository.findByEmail(clean).ifPresent(u -> {
                                if ((u.getActive() == null || Boolean.TRUE.equals(u.getActive())) && "Citizen".equalsIgnoreCase(u.getRole())) {
                                    recipientEmails.add(clean);
                                }
                            });
                        }
                    }
                } else if ("TARGETED".equals(mode)) {
                    // B. Targeted alert: Target selected Citizens by email/ID or specific sector
                    if (alert.getTargetRecipients() != null && !alert.getTargetRecipients().isBlank()) {
                        for (String item : alert.getTargetRecipients().split("[,;\\s]+")) {
                            String clean = item.trim().toLowerCase();
                            if (clean.matches("\\d+")) {
                                try {
                                    Long uid = Long.parseLong(clean);
                                    userRepository.findById(uid).ifPresent(u -> {
                                        if ((u.getActive() == null || Boolean.TRUE.equals(u.getActive())) && "Citizen".equalsIgnoreCase(u.getRole())) {
                                            recipientEmails.add(u.getEmail().trim().toLowerCase());
                                        }
                                    });
                                } catch (Exception ignored) {}
                            } else {
                                userRepository.findByEmail(clean).ifPresent(u -> {
                                    if ((u.getActive() == null || Boolean.TRUE.equals(u.getActive())) && "Citizen".equalsIgnoreCase(u.getRole())) {
                                        recipientEmails.add(clean);
                                    }
                                });
                            }
                        }
                    } else if (alert.getRegion() != null && !alert.getRegion().isBlank() 
                               && !"All".equalsIgnoreCase(alert.getRegion()) 
                               && !"All Citizens".equalsIgnoreCase(alert.getRegion())
                               && !"All India".equalsIgnoreCase(alert.getRegion())
                               && !"Tamil Nadu".equalsIgnoreCase(alert.getRegion())) {
                        List<User> allCitizens = userRepository.findByRole("Citizen");
                        for (User c : allCitizens) {
                            if ((c.getActive() == null || Boolean.TRUE.equals(c.getActive())) && c.getEmail() != null) {
                                boolean match = (c.getLocation() != null && c.getLocation().toLowerCase().contains(alert.getRegion().toLowerCase().trim()))
                                             || (c.getAssignedZone() != null && c.getAssignedZone().equalsIgnoreCase(alert.getRegion().trim()));
                                if (match) {
                                    recipientEmails.add(c.getEmail().trim().toLowerCase());
                                }
                            }
                        }
                    }
                } else {
                    // A. Broadcast alert (Default): Send official emergency alert to ALL eligible (active) Citizens
                    List<User> allCitizens = userRepository.findByRole("Citizen");
                    for (User c : allCitizens) {
                        if ((c.getActive() == null || Boolean.TRUE.equals(c.getActive())) && c.getEmail() != null && !c.getEmail().isBlank()) {
                            recipientEmails.add(c.getEmail().trim().toLowerCase());
                        }
                    }
                }

                String prio = alert.getSeverity() != null ? alert.getSeverity() : "High";
                for (String recipientEmail : recipientEmails) {
                    notificationService.createNotification(
                        recipientEmail,
                        "Citizen",
                        alert.getTitle(), // Real Alert Title!
                        alert.getMessage(), // Real Alert Message!
                        "WARNINGS", // Matches citizen notification center category
                        prio,
                        savedAlert.getAlertId(),
                        "ALERT",
                        "citizen-notifications.html"
                    );
                }
                log.info("Disaster alert #{} created and dispatched {} notification records to citizens (Mode: {})", 
                         savedAlert.getAlertId(), recipientEmails.size(), mode);

                notificationService.notifyRespondersInRegion(
                    alert.getRegion(),
                    "Emergency Alert Broadcast: " + alert.getTitle(),
                    alert.getMessage(),
                    "HAZARDS",
                    prio,
                    savedAlert.getAlertId(),
                    "ALERT",
                    "responder-notifications.html"
                );

                notificationService.notifyAdmins(
                    "Emergency Alert Broadcasted: " + alert.getTitle(),
                    "Disaster alert created for region " + alert.getRegion() + ": " + alert.getMessage(),
                    "ALERTS",
                    prio,
                    savedAlert.getAlertId(),
                    "ALERT",
                    "admin-notifications.html"
                );
            } catch (Exception e) {
                log.warn("Notification dispatch warning on createAlert: {}", e.getMessage());
            }
        }

        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new EmergencyBroadcastCreatedEvent(savedAlert, adminEmail));
            } catch (Exception e) {
                log.warn("Event publication warning on createAlert: {}", e.getMessage());
            }
        }

        return savedAlert;
    }

    private void createTaskForResponder(Alert alert, User responder, String createdBy) {
        RescueTask task = new RescueTask();
        task.setTitle("Alert Response: " + alert.getTitle());
        task.setDescription("Respond to alert in " + alert.getRegion() + ". " + alert.getMessage());
        task.setZone(alert.getRegion());
        String alertPrio = "Medium";
        if (alert.getSeverity() != null) {
            String s = alert.getSeverity().trim();
            if (s.equalsIgnoreCase("Critical") || s.equalsIgnoreCase("Severe") || s.equalsIgnoreCase("High") || s.equalsIgnoreCase("Extreme")) {
                alertPrio = "High";
            } else if (s.equalsIgnoreCase("Moderate") || s.equalsIgnoreCase("Medium")) {
                alertPrio = "Medium";
            } else if (s.equalsIgnoreCase("Minor") || s.equalsIgnoreCase("Low")) {
                alertPrio = "Low";
            }
        }
        task.setPriority(alertPrio);
        task.setCreatedBy(createdBy);
        task.setAssignedTo(responder.getEmail());
        task.setAssignedResponderName(responder.getName());
        task.setLatitude(alert.getLatitude());
        task.setLongitude(alert.getLongitude());
        task.setLocationName(alert.getRegion());
        task.setStatus("Pending");
        task.setCreatedAt(LocalDateTime.now());
        rescueTaskService.createTask(task);
    }

    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    public List<Alert> getAlertsByRegion(String region) {
        return alertRepository.findByRegion(region);
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByStatus("Active");
    }

    public List<Alert> getBroadcastedAlerts() {
        return alertRepository.findByBroadcastedTrueOrderByCreatedAtDesc();
    }

    public Alert getAlertById(Long alertId) {
        return alertRepository.findById(alertId).orElse(null);
    }

    @Transactional
    public Alert acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert != null) {
            alert.setStatus("Acknowledged");
            return alertRepository.save(alert);
        }
        return null;
    }

    @Transactional
    public Alert resolveAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert != null) {
            alert.setStatus("Resolved");
            return alertRepository.save(alert);
        }
        return null;
    }

    @Transactional
    public Alert broadcastAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert != null) {
            alert.setBroadcasted(true);
            Alert saved = alertRepository.save(alert);
            if (notificationService != null) {
                try {
                    notificationService.notifyCitizensInRegion(
                        saved.getRegion(),
                        "Broadcasted Alert: " + saved.getRegion(),
                        saved.getMessage(),
                        "ALERTS",
                        saved.getSeverity() != null ? saved.getSeverity() : "High",
                        saved.getAlertId(),
                        "ALERT",
                        "citizen-notifications.html"
                    );
                    notificationService.notifyRespondersInRegion(
                        saved.getRegion(),
                        "Emergency Alert Broadcast: " + saved.getRegion(),
                        saved.getMessage(),
                        "ALERTS",
                        saved.getSeverity() != null ? saved.getSeverity() : "High",
                        saved.getAlertId(),
                        "ALERT",
                        "responder-notifications.html"
                    );
                    notificationService.notifyAdmins(
                        "Emergency Broadcast: " + saved.getTitle(),
                        "Broadcast transmitted for " + saved.getRegion() + ": " + saved.getMessage(),
                        "ALERTS",
                        saved.getSeverity() != null ? saved.getSeverity() : "High",
                        saved.getAlertId(),
                        "ALERT",
                        "admin-notifications.html"
                    );
                } catch (Exception e) {
                    log.warn("Notification dispatch warning on broadcastAlert: {}", e.getMessage());
                }
            }

            if (eventPublisher != null) {
                try {
                    eventPublisher.publishEvent(new EmergencyBroadcastCreatedEvent(saved, "Central Command Admin"));
                } catch (Exception e) {
                    log.warn("Event publication warning on broadcastAlert: {}", e.getMessage());
                }
            }

            return saved;
        }
        return null;
    }

    @Transactional
    public Alert updateAlert(Alert alert) {
        return alertRepository.save(alert);
    }
}
