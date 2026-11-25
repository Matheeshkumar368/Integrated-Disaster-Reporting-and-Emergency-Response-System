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

        String cleanEmail = recipientEmail.trim().toLowerCase();

        // Duplicate notification prevention
        if (relatedId != null && relatedType != null) {
            Optional<Notification> existing = notificationRepository.findFirstByRecipientEmailAndTitleAndRelatedIncidentIdAndRelatedIncidentTypeOrderByCreatedAtDesc(
                cleanEmail, title, relatedId, relatedType
            );
            if (existing.isPresent()) {
                log.info("Duplicate notification prevented for [{}] - title: '{}', relatedId: {}, type: {}", cleanEmail, title, relatedId, relatedType);
                return existing.get();
            }
        } else {
            // General announcements or broadcasts without related incident id: prevent duplicate if within 15 seconds
            Optional<Notification> recent = notificationRepository.findFirstByRecipientEmailAndTitleAndMessageOrderByCreatedAtDesc(
                cleanEmail, title, message
            );
            if (recent.isPresent() && recent.get().getCreatedAt() != null &&
                recent.get().getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(15))) {
                log.info("Duplicate notification throttled for [{}] within 15s - title: '{}'", cleanEmail, title);
                return recent.get();
            }
        }

        Notification notification = new Notification(
            cleanEmail,
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
        log.info("Notification #{} dispatched to [{}] (Role: {}): {}", saved.getId(), cleanEmail, recipientRole, title);
        return saved;
    }

    @Transactional
    public List<Notification> notifyAdmins(String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        List<User> admins = userRepository.findByRole("Admin");
        List<Notification> created = new ArrayList<>();

        if (admins == null || admins.isEmpty()) {
            log.warn("No registered Admin users found in database to receive notification: title='{}'", title);
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
        return notifyCitizensInRegion(null, title, message, category, priority, relatedId, relatedType, actionUrl);
    }

    @Transactional
    public List<Notification> notifyCitizensInRegion(String region, String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        List<User> citizens = userRepository.findByRole("Citizen");
        List<Notification> created = new ArrayList<>();
        if (citizens == null) return created;

        boolean isStatewideOrAll = region == null || region.trim().isEmpty() || 
            "All".equalsIgnoreCase(region) || "Tamil Nadu".equalsIgnoreCase(region) || 
            "Tamil Nadu (Statewide)".equalsIgnoreCase(region) || "Statewide".equalsIgnoreCase(region);

        for (User citizen : citizens) {
            if (citizen.getEmail() == null || citizen.getEmail().isBlank()) continue;
            boolean match = isStatewideOrAll;
            if (!match && citizen.getLocation() != null && citizen.getLocation().toLowerCase().contains(region.toLowerCase().trim())) {
                match = true;
            }
            if (!match && citizen.getAssignedZone() != null && citizen.getAssignedZone().equalsIgnoreCase(region.trim())) {
                match = true;
            }
            // General safety delivery for registered citizens without a specific region lock
            if (!match && (citizen.getLocation() == null || citizen.getLocation().isBlank())) {
                match = true;
            }
            if (match) {
                Notification n = createNotification(citizen.getEmail(), "Citizen", title, message, category, priority, relatedId, relatedType, actionUrl);
                if (n != null) created.add(n);
            }
        }
        return created;
    }

    @Transactional
    public List<Notification> notifyAllResponders(String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        return notifyRespondersInRegion(null, title, message, category, priority, relatedId, relatedType, actionUrl);
    }

    @Transactional
    public List<Notification> notifyRespondersInRegion(String region, String title, String message, String category, String priority, Long relatedId, String relatedType, String actionUrl) {
        List<User> responders = userRepository.findByRole("Responder");
        List<Notification> created = new ArrayList<>();
        if (responders == null) return created;

        boolean isStatewideOrAll = region == null || region.trim().isEmpty() || 
            "All".equalsIgnoreCase(region) || "Tamil Nadu".equalsIgnoreCase(region) || 
            "Tamil Nadu (Statewide)".equalsIgnoreCase(region) || "Statewide".equalsIgnoreCase(region);

        for (User responder : responders) {
            if (responder.getEmail() == null || responder.getEmail().isBlank()) continue;
            boolean match = isStatewideOrAll;
            if (!match && responder.getAssignedZone() != null && responder.getAssignedZone().equalsIgnoreCase(region.trim())) {
                match = true;
            }
            if (!match && responder.getLocation() != null && responder.getLocation().toLowerCase().contains(region.toLowerCase().trim())) {
                match = true;
            }
            if (!match && (responder.getAssignedZone() == null || "Unassigned".equalsIgnoreCase(responder.getAssignedZone()))) {
                match = true;
            }
            if (match) {
                Notification n = createNotification(responder.getEmail(), "Responder", title, message, category, priority, relatedId, relatedType, actionUrl);
                if (n != null) created.add(n);
            }
        }
        return created;
    }

    public List<Notification> getAdminNotifications(String adminEmail, String category, String status) {
        if (adminEmail == null || adminEmail.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String email = adminEmail.trim().toLowerCase();
        String normStatus = status != null ? status.trim().toLowerCase() : "all";
        List<Notification> list;

        if ("unread".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Admin", false);
        } else if ("read".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Admin", true);
        } else {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Admin");
        }

        if (category == null || category.trim().isEmpty() || "ALL".equalsIgnoreCase(category.trim())) {
            return list;
        }

        String filterCat = category.trim().toUpperCase();
        return list.stream().filter(n -> {
            String c = (n.getCategory() != null ? n.getCategory() : "").toUpperCase().trim();
            String rel = (n.getRelatedIncidentType() != null ? n.getRelatedIncidentType() : "").toUpperCase().trim();
            String title = (n.getTitle() != null ? n.getTitle() : "").toUpperCase();

            if ("REPORTS".equals(filterCat) || "DISASTER_REPORT".equals(filterCat) || "REPORT_STATUS".equals(filterCat)) {
                return "CITIZEN_REPORT".equals(rel) || c.contains("DISASTER") || c.contains("REPORT") || title.contains("REPORT") || title.contains("DISASTER");
            } else if ("ASSIGNMENTS".equals(filterCat) || "RESPONDER_ASSIGNMENT".equals(filterCat)) {
                return "TASK".equals(rel) || c.contains("ASSIGNMENT") || c.contains("RESPONSE") || title.contains("TASK") || title.contains("EN ROUTE") || title.contains("ARRIVED") || title.contains("RESPONDER") || title.contains("MISSION");
            } else if ("BROADCASTS".equals(filterCat) || "EMERGENCY_BROADCAST".equals(filterCat)) {
                return "ALERT".equals(rel) || c.contains("ALERT") || c.contains("BROADCAST") || title.contains("BROADCAST") || title.contains("ALERT") || title.contains("WARNING");
            } else if ("NDMA".equals(filterCat) || "NDMA_ALERT".equals(filterCat)) {
                return "OFFICIAL_ALERT".equals(rel) || title.contains("NDMA") || title.contains("OFFICIAL");
            } else if ("SYSTEM".equals(filterCat) || "SYSTEM_EVENT".equals(filterCat)) {
                return c.contains("SYSTEM") || c.contains("USER") || title.contains("SECURITY") || title.contains("PIN") || title.contains("AUDIT");
            } else if ("DIRECT_MESSAGE".equals(filterCat) || "MESSAGE".equals(filterCat) || "MESSAGES".equals(filterCat)) {
                return "MESSAGE".equals(rel) || c.contains("MESSAGE") || title.contains("MESSAGE");
            }
            return c.equals(filterCat);
        }).toList();
    }

    public List<Notification> getCitizenNotifications(String citizenEmail, String category, String status) {
        if (citizenEmail == null || citizenEmail.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String email = citizenEmail.trim().toLowerCase();
        String normStatus = status != null ? status.trim().toLowerCase() : "all";
        List<Notification> list;

        if ("unread".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Citizen", false);
        } else if ("read".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Citizen", true);
        } else {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Citizen");
        }

        if (category == null || category.trim().isEmpty() || "ALL".equalsIgnoreCase(category.trim())) {
            return list;
        }

        String filterCat = category.trim().toUpperCase();
        return list.stream().filter(n -> {
            String c = (n.getCategory() != null ? n.getCategory() : "").toUpperCase().trim();
            String rel = (n.getRelatedIncidentType() != null ? n.getRelatedIncidentType() : "").toUpperCase().trim();
            String title = (n.getTitle() != null ? n.getTitle() : "").toUpperCase();

            if ("REPORTS".equals(filterCat) || "DISASTER_REPORT".equals(filterCat) || "REPORT_STATUS".equals(filterCat)) {
                return "CITIZEN_REPORT".equals(rel) || c.contains("REPORT") || title.contains("REPORT") || title.contains("DISASTER");
            } else if ("RESCUE".equals(filterCat) || "RESPONDER_ASSIGNMENT".equals(filterCat)) {
                return "TASK".equals(rel) || c.contains("RESPONSE") || c.contains("ASSIGNMENT") || title.contains("RESCUE") || title.contains("DEPLOYED") || title.contains("EN ROUTE") || title.contains("ARRIVED") || title.contains("COMPLETED");
            } else if ("WARNINGS".equals(filterCat) || "EMERGENCY_BROADCAST".equals(filterCat)) {
                return "ALERT".equals(rel) || c.contains("ALERT") || c.contains("BROADCAST") || title.contains("ALERT") || title.contains("WARNING") || title.contains("EVACUATION");
            } else if ("MESSAGES".equals(filterCat) || "DIRECT_MESSAGE".equals(filterCat)) {
                return "MESSAGE".equals(rel) || c.contains("MESSAGE") || title.contains("SAFETY") || title.contains("DIRECTIVE") || title.contains("INSTRUCTION") || title.contains("MESSAGE");
            } else if ("NDMA".equals(filterCat) || "NDMA_ALERT".equals(filterCat)) {
                return "OFFICIAL_ALERT".equals(rel) || title.contains("NDMA") || title.contains("OFFICIAL");
            } else if ("SYSTEM".equals(filterCat) || "SYSTEM_EVENT".equals(filterCat)) {
                return c.contains("SYSTEM") || c.contains("USER");
            }
            return c.equals(filterCat);
        }).toList();
    }

    public List<Notification> getResponderNotifications(String responderEmail, String category, String status) {
        if (responderEmail == null || responderEmail.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String email = responderEmail.trim().toLowerCase();
        String normStatus = status != null ? status.trim().toLowerCase() : "all";
        List<Notification> list;

        if ("unread".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Responder", false);
        } else if ("read".equals(normStatus)) {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Responder", true);
        } else {
            list = notificationRepository.findByRecipientEmailAndRecipientRoleIgnoreCaseAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(email, "Responder");
        }

        if (category == null || category.trim().isEmpty() || "ALL".equalsIgnoreCase(category.trim())) {
            return list;
        }

        String filterCat = category.trim().toUpperCase();
        return list.stream().filter(n -> {
            String c = (n.getCategory() != null ? n.getCategory() : "").toUpperCase().trim();
            String rel = (n.getRelatedIncidentType() != null ? n.getRelatedIncidentType() : "").toUpperCase().trim();
            String title = (n.getTitle() != null ? n.getTitle() : "").toUpperCase();

            if ("DISPATCH".equals(filterCat) || "RESPONDER_ASSIGNMENT".equals(filterCat)) {
                return "TASK".equals(rel) || c.contains("ASSIGNMENT") || c.contains("DISPATCH") || title.contains("ASSIGNMENT") || title.contains("DISPATCH") || title.contains("MISSION");
            } else if ("STATUS_LOG".equals(filterCat) || "REPORT_STATUS".equals(filterCat)) {
                return c.contains("RESPONSE") || title.contains("ACKNOWLEDGED") || title.contains("STATUS") || title.contains("EN ROUTE") || title.contains("ARRIVED") || title.contains("COMPLETED");
            } else if ("HQ_UPDATES".equals(filterCat) || "DIRECT_MESSAGE".equals(filterCat) || "MESSAGES".equals(filterCat)) {
                return "MESSAGE".equals(rel) || c.contains("MESSAGE") || title.contains("HQ") || title.contains("COMMAND") || title.contains("ORDER") || title.contains("INSTRUCTION");
            } else if ("HAZARDS".equals(filterCat) || "EMERGENCY_BROADCAST".equals(filterCat)) {
                return "ALERT".equals(rel) || c.contains("ALERT") || c.contains("BROADCAST") || title.contains("ALERT") || title.contains("WARNING") || title.contains("HAZARD");
            } else if ("NDMA".equals(filterCat) || "NDMA_ALERT".equals(filterCat)) {
                return "OFFICIAL_ALERT".equals(rel) || title.contains("NDMA") || title.contains("OFFICIAL");
            } else if ("SYSTEM".equals(filterCat) || "SYSTEM_EVENT".equals(filterCat)) {
                return c.contains("SYSTEM");
            }
            return c.equals(filterCat);
        }).toList();
    }

    public Map<String, Object> getRoleNotificationSummary(String callerEmail, String role) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return Map.of("total", 0L, "unread", 0L, "unreadCount", 0L);
        }

        String email = callerEmail.trim().toLowerCase();
        long unread = notificationRepository.countByRecipientEmailAndRecipientRoleIgnoreCaseAndIsReadFalseAndIsDeletedByRecipientFalse(email, role);
        long total = notificationRepository.countByRecipientEmailAndRecipientRoleIgnoreCaseAndIsDeletedByRecipientFalse(email, role);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("unread", unread);
        summary.put("unreadCount", unread);
        return summary;
    }

    @Transactional
    public int markAllAsReadForRole(String callerEmail, String role) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return 0;
        }
        return notificationRepository.markAllAsReadForRecipientAndRole(callerEmail.trim().toLowerCase(), role);
    }

    public List<Notification> getNotificationsForUser(String callerEmail, String category, String status) {
        if (callerEmail == null || callerEmail.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String email = callerEmail.trim().toLowerCase();

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
