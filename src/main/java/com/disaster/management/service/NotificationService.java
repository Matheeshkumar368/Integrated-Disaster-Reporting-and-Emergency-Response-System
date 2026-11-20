package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.Notification;
import com.disaster.management.entity.User;
import com.disaster.management.repository.NotificationRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Notification createNotification(String recipientEmail, String recipientRole, String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            log.warn("Cannot create notification without recipient email: title={}", title);
            return null;
        }

        Notification notification = new Notification(
            recipientEmail.trim().toLowerCase(),
            recipientRole,
            title,
            message,
            category != null ? category.trim().toUpperCase() : "UPDATE",
            priority != null ? priority : "Medium",
            relatedId,
            relatedType,
            actionUrl
        );

        Notification saved = notificationRepository.save(notification);
        log.info("Notification #{} dispatched to [{}] (Role: {}): {}", saved.getId(), recipientEmail, recipientRole, title);
        return saved;
    }

    @Transactional
    public List<Notification> notifyAdmins(String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        List<User> admins = userRepository.findByRole("Admin");
        List<Notification> created = new ArrayList<>();

        if (admins == null || admins.isEmpty()) {
            Notification fallback = createNotification("admin@disaster.gov.in", "Admin", title, message, category, priority, relatedId, relatedType, actionUrl);
            if (fallback != null) created.add(fallback);
            return created;
        }

        for (User admin : admins) {
            if (admin.getEmail() != null) {
                Notification n = createNotification(admin.getEmail(), "Admin", title, message, category, priority, relatedId, relatedType, actionUrl);
                if (n != null) created.add(n);
            }
        }
        return created;
    }

    @Transactional
    public Notification notifyResponder(String responderEmail, String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        return createNotification(responderEmail, "Responder", title, message, category, priority, relatedId, relatedType, actionUrl);
    }

    @Transactional
    public Notification notifyCitizen(String citizenEmail, String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        return createNotification(citizenEmail, "Citizen", title, message, category, priority, relatedId, relatedType, actionUrl);
    }

    @Transactional
    public List<Notification> notifyAllCitizens(String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        List<User> citizens = userRepository.findByRole("Citizen");
        List<Notification> created = new ArrayList<>();

        if (citizens != null) {
            for (User citizen : citizens) {
                if (citizen.getEmail() != null) {
                    Notification n = createNotification(citizen.getEmail(), "Citizen", title, message, category, priority, relatedId, relatedType, actionUrl);
                    if (n != null) created.add(n);
                }
            }
        }
        return created;
    }

    @Transactional
    public void ensureBaselineNotifications(String email) {
        if (email == null || email.isBlank()) return;
        String cleanEmail = email.toLowerCase().trim();
        long count = notificationRepository.countByRecipientEmailAndIsDeletedByRecipientFalse(cleanEmail);
        if (count > 0) return;

        Optional<User> userOpt = userRepository.findByEmail(cleanEmail);
        String role = userOpt.map(User::getRole).orElse("Citizen");

        if ("Admin".equalsIgnoreCase(role)) {
            createNotification(cleanEmail, "Admin", "System Maintenance Scheduled", "The system will undergo maintenance tonight at 11:00 PM.", "SYSTEM", "Medium", null, "SYSTEM", "/admin-dashboard.html");
            createNotification(cleanEmail, "Admin", "New User Registered", "A new citizen has joined the platform.", "USERS", "Low", null, "USER", "/admin-users.html");
            createNotification(cleanEmail, "Admin", "New Disaster Reported", "Flood reported in Karur District.", "DISASTERS", "High", null, "DISASTER", "/admin-dashboard.html?disasters");
            createNotification(cleanEmail, "Admin", "New Responder Registered", "Emergency responder joined active duty roster.", "RESPONDERS", "Medium", null, "USER", "/admin-users.html");
        } else if ("Responder".equalsIgnoreCase(role)) {
            createNotification(cleanEmail, "Responder", "New Rescue Assignment", "Affected area: Karur, Tamil Nadu", "ASSIGNMENTS", "New", null, "TASK", "/responder-dashboard.html?tasks");
            createNotification(cleanEmail, "Responder", "Operation Update", "Rescue operation is in progress at the reported location.", "UPDATES", "Medium", null, "TASK", "/responder-dashboard.html?tasks");
            createNotification(cleanEmail, "Responder", "Emergency Broadcast Message", "Advisory: High wind warning for delta operations.", "MESSAGES", "Medium", null, "ALERT", "/responder-dashboard.html?warnings");
            createNotification(cleanEmail, "Responder", "Mission Completed", "Evacuation completed in Zone B.", "RESPONSE", "Low", null, "TASK", "/responder-dashboard.html?tasks");
        } else {
            createNotification(cleanEmail, "Citizen", "Severe Flood Alert – Karur District", "Heavy rainfall is expected. Avoid low-lying areas and follow official advisories.", "ALERTS", "High", null, "ALERT", "/citizen-dashboard.html?alerts");
            createNotification(cleanEmail, "Citizen", "Your Report is Under Review", "Report #DR-20261101-001 has been received and is being verified.", "UPDATES", "Medium", null, "REPORT", "/citizen-dashboard.html?my-reports");
            createNotification(cleanEmail, "Citizen", "Rescue Team Deployed", "A rescue team has been dispatched to your reported location.", "RESPONSE", "Medium", null, "REPORT", "/citizen-dashboard.html?my-reports");
            createNotification(cleanEmail, "Citizen", "Community Safety Advisory", "Emergency relief centers and helplines are open 24/7.", "MESSAGES", "Low", null, "ALERT", "/citizen-dashboard.html?alerts");
        }
    }

    public List<Notification> getNotificationsForUser(String callerEmail, String category, String status) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String email = callerEmail.trim().toLowerCase();
        try {
            ensureBaselineNotifications(email);
        } catch (Exception e) {
            log.warn("Baseline notification check: {}", e.getMessage());
        }

        String normStatus = status != null ? status.trim().toLowerCase() : "all";
        List<Notification> list;

        if ("unread".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, false);
        } else if ("read".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, true);
        } else {
            list = notificationRepository.findByRecipientEmailAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email);
        }

        if (category == null || category.trim().isEmpty() || "ALL".equalsIgnoreCase(category.trim())) {
            return list;
        }

        String filterCat = category.trim().toUpperCase();
        return list.stream().filter(n -> {
            String c = (n.getCategory() != null ? n.getCategory() : "").toUpperCase().trim();
            if (c.equals(filterCat)) return true;
            // Handle singular / plural equivalence (e.g. ALERT vs ALERTS, UPDATE vs UPDATES, USER vs USERS)
            if (filterCat.endsWith("S") && c.equals(filterCat.substring(0, filterCat.length() - 1))) return true;
            if (c.endsWith("S") && filterCat.equals(c.substring(0, c.length() - 1))) return true;
            
            // Keyword fallback for category groupings
            String title = (n.getTitle() != null ? n.getTitle() : "").toUpperCase();
            if ("USERS".equals(filterCat) && title.contains("USER")) return true;
            if ("RESPONDERS".equals(filterCat) && title.contains("RESPONDER")) return true;
            if ("DISASTERS".equals(filterCat) && (title.contains("DISASTER") || title.contains("FLOOD") || title.contains("INCIDENT") || title.contains("REPORT"))) return true;
            if ("ASSIGNMENTS".equals(filterCat) && (title.contains("ASSIGNMENT") || title.contains("MISSION") || title.contains("RESCUE") || title.contains("TASK"))) return true;
            if ("ALERTS".equals(filterCat) && (title.contains("ALERT") || title.contains("WARNING") || title.contains("CYCLONE"))) return true;
            if ("MESSAGES".equals(filterCat) && (title.contains("MESSAGE") || title.contains("ADVISORY") || title.contains("BROADCAST"))) return true;
            if ("RESPONSE".equals(filterCat) && (title.contains("DEPLOYED") || title.contains("ARRIVED") || title.contains("EN ROUTE") || title.contains("COMPLETED") || title.contains("RESOLVED"))) return true;
            if ("UPDATES".equals(filterCat) && (title.contains("UPDATE") || title.contains("STATUS") || title.contains("VERIFIED") || title.contains("RECEIVED") || title.contains("REVIEW"))) return true;
            return false;
        }).toList();
    }

    public Map<String, Object> getNotificationSummary(String callerEmail) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return Map.of("total", 0, "unread", 0);
        }

        String email = callerEmail.trim().toLowerCase();
        try {
            ensureBaselineNotifications(email);
        } catch (Exception e) {
            log.warn("Baseline notification check in summary: {}", e.getMessage());
        }
        long unread = notificationRepository.countByRecipientEmailAndIsReadFalseAndIsDeletedByRecipientFalse(email);
        long total = notificationRepository.countByRecipientEmailAndIsDeletedByRecipientFalse(email);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("unread", unread);
        summary.put("unreadCount", unread);
        return summary;
    }

    @Transactional
    public Notification markAsRead(Long id, String callerEmail) {
        if (id == null || callerEmail == null) {
            throw new IllegalArgumentException("Notification ID and caller email are required.");
        }

        String email = callerEmail.trim().toLowerCase();
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found with ID: " + id));

        // Enforce user isolation: a user can only read their own notification
        if (!email.equalsIgnoreCase(notification.getRecipientEmail())) {
            log.warn("Access Denied: User [{}] attempted to mark notification #{} belonging to [{}]", email, id, notification.getRecipientEmail());
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    @Transactional
    public int markAllAsRead(String callerEmail) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return 0;
        }
        return notificationRepository.markAllAsReadForRecipient(callerEmail.trim().toLowerCase());
    }

    @Transactional
    public void deleteNotification(Long id, String callerEmail) {
        if (id == null || callerEmail == null) {
            throw new IllegalArgumentException("Notification ID and caller email are required.");
        }

        String email = callerEmail.trim().toLowerCase();
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found with ID: " + id));

        // Enforce user isolation: a user can only delete notifications from their own personal list
        if (!email.equalsIgnoreCase(notification.getRecipientEmail())) {
            log.warn("Access Denied: User [{}] attempted to delete notification #{} belonging to [{}]", email, id, notification.getRecipientEmail());
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }

        // Safe personal delete (leaves actual incident, task, and other users' records untouched)
        notification.setIsDeletedByRecipient(true);
        notificationRepository.save(notification);
        log.info("Notification #{} removed from personal list for [{}]", id, email);
    }
}
