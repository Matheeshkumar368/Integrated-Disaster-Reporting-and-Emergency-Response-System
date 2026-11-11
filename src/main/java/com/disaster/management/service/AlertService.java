package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.Alert;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.AlertRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RescueTaskService rescueTaskService;

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
    public Alert updateAlert(Alert alert) {
        return alertRepository.save(alert);
    }
}
