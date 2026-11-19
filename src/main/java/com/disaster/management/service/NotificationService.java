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

    public List<Notification> getNotificationsForUser(String callerEmail, String category, String status) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String email = callerEmail.trim().toLowerCase();
        boolean hasCategory = category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category.trim());
        String cat = hasCategory ? category.trim().toUpperCase() : null;

        String normStatus = status != null ? status.trim().toLowerCase() : "all";

        if ("unread".equals(normStatus)) {
            if (hasCategory) {
                return notificationRepository.findByRecipientEmailAndCategoryIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, cat, false);
            } else {
                return notificationRepository.findByRecipientEmailAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, false);
            }
        } else if ("read".equals(normStatus)) {
            if (hasCategory) {
                return notificationRepository.findByRecipientEmailAndCategoryIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, cat, true);
            } else {
                return notificationRepository.findByRecipientEmailAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, true);
            }
        } else {
            if (hasCategory) {
                return notificationRepository.findByRecipientEmailAndCategoryIgnoreCaseAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, cat);
            } else {
                return notificationRepository.findByRecipientEmailAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email);
            }
        }
    }

    public Map<String, Object> getNotificationSummary(String callerEmail) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return Map.of("total", 0, "unread", 0);
        }

        String email = callerEmail.trim().toLowerCase();
        long unread = notificationRepository.countByRecipientEmailAndIsReadFalseAndIsDeletedByRecipientFalse(email);
        long total = notificationRepository.countByRecipientEmailAndIsDeletedByRecipientFalse(email);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("unread", unread);
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
