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

    @Transactional
    public void ensureBaselineNotifications(String email) {
        if (email == null || email.isBlank()) return;
        String cleanEmail = email.toLowerCase().trim();
        long count = notificationRepository.countByRecipientEmailAndIsDeletedByRecipientFalse(cleanEmail);
        if (count > 0) return;

        Optional<User> userOpt = userRepository.findByEmail(cleanEmail);
        String role = userOpt.map(User::getRole).orElse("Citizen");

        if ("Admin".equalsIgnoreCase(role)) {
            createNotification(cleanEmail, "Admin", "New Citizen Disaster Report #CR-104", "Severe flash flood reported by citizen in Karur riverbank area.", "REPORTS", "High", 104L, "CITIZEN_REPORT", "/admin-dashboard.html");
            createNotification(cleanEmail, "Admin", "New Citizen Registered", "Citizen Rahul Sharma registered on Public Safety Portal.", "SYSTEM", "Low", null, "USER", "/admin-users.html");
            createNotification(cleanEmail, "Admin", "New Responder Registered", "Emergency Field Responder Amit Kumar joined active duty roster.", "SYSTEM", "Medium", null, "USER", "/admin-users.html");
            createNotification(cleanEmail, "Admin", "Official NDMA Alert Ingested", "CAP 1.2 Feed: Red Alert Cyclone Warning for Coastal Belt.", "BROADCASTS", "Critical", null, "ALERT", "/admin-dashboard.html");
            createNotification(cleanEmail, "Admin", "Emergency Broadcast Transmitted", "Statewide evacuation advisory broadcasted to 2,400 citizens.", "BROADCASTS", "High", null, "ALERT", "/admin-alerts.html");
            createNotification(cleanEmail, "Admin", "Responder Unit Alpha Dispatched", "Unit Alpha-1 assigned to Incident #CR-104 in Karur.", "ASSIGNMENTS", "High", 104L, "TASK", "/admin-tasks.html");
            createNotification(cleanEmail, "Admin", "Mission Progress: En Route", "Unit Alpha-1 is en route to scene. Speed: 42 km/h • ETA: 6 mins.", "ASSIGNMENTS", "Medium", 104L, "TASK", "/admin-tasks.html");
            createNotification(cleanEmail, "Admin", "Mission Completed: Rescue Sector 4", "Evacuation safely executed. 14 citizens sheltered.", "ASSIGNMENTS", "Low", 104L, "TASK", "/admin-tasks.html");
            createNotification(cleanEmail, "Admin", "System Health Check Passed", "All database replication, GIS mapping, and telemetry gateways nominal.", "SYSTEM", "Low", null, "SYSTEM", "/admin-dashboard.html");
        } else if ("Responder".equalsIgnoreCase(role)) {
            createNotification(cleanEmail, "Responder", "New Rescue Assignment #RT-401", "Dispatch order for Incident #CR-104 (Flash Flood Distress). Immediate response required.", "DISPATCH", "Critical", 401L, "TASK", "/responder-dashboard.html");
            createNotification(cleanEmail, "Responder", "Emergency Location & Incident Details", "Target: Riverbank Sector 4 • Coordinates: 10.9571° N, 78.0766° E • Water Depth: 1.4m.", "DISPATCH", "High", 401L, "TASK", "/responder-dashboard.html");
            createNotification(cleanEmail, "Responder", "Assignment Priority: Urgent Critical", "Priority designated Level 1 Critical due to rising flood waters and trapped families.", "DISPATCH", "Critical", 401L, "TASK", "/responder-dashboard.html");
            createNotification(cleanEmail, "Responder", "Mission Instructions: Evacuate Sector 4", "Deploy inflatable rescue boats. Transfer evacuees to High School Relief Camp.", "DISPATCH", "High", 401L, "TASK", "/responder-dashboard.html");
            createNotification(cleanEmail, "Responder", "Assignment Acknowledged", "Dispatch center recorded your acknowledgment for Task #RT-401.", "STATUS_LOG", "Medium", 401L, "TASK", "/responder-dashboard.html");
            createNotification(cleanEmail, "Responder", "Central Command Advisory Update", "Secondary access bridge submerged. Route via Eastern Bypass Highway.", "HQ_UPDATES", "High", 401L, "TASK", "/responder-dashboard.html");
            createNotification(cleanEmail, "Responder", "Mission Status Changed to En Route", "Telemetry telemetry synchronized. Central Command monitoring live GPS.", "STATUS_LOG", "Medium", 401L, "TASK", "/responder-dashboard.html");
            createNotification(cleanEmail, "Responder", "Official Hazard Warning: Gale Force Winds", "NDMA Sachet Warning: 65 km/h gusts expected. Secure aerial and watercraft equipment.", "HAZARDS", "High", null, "ALERT", "/responder-dashboard.html");
        } else {
            createNotification(cleanEmail, "Citizen", "Report Submitted: Confirmation #CR-104", "Your disaster report has been recorded and transmitted to Central Command.", "REPORTS", "Medium", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Report Verified by Command", "Incident #CR-104 has been verified by emergency authorities. Priority set to High.", "REPORTS", "High", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Rescue Team Assigned", "Emergency Rescue Team Alpha-1 has been dispatched to your distress coordinates.", "RESCUE", "High", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Responder Acknowledged Assignment", "Responder Amit Kumar (Alpha-1) accepted dispatch and prepared gear.", "RESCUE", "Medium", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Responder En Route", "Rescue Vehicle Alpha-1 is en route to your location. Estimated arrival in 7 minutes.", "RESCUE", "High", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Responder Arrived on Scene", "Rescue Team Alpha-1 has arrived at your reported location. Please signal if safe.", "RESCUE", "Critical", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Mission Progress: Assistance in Progress", "Emergency medical and evacuation assistance actively underway.", "RESCUE", "High", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Mission Completed & Resolved", "Rescue operation safely completed. Your report is now resolved.", "RESCUE", "Low", 104L, "CITIZEN_REPORT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Official Disaster Warning: Severe Flood", "IMD & NDMA Advisory: Avoid low-lying riverbank areas. Relief centers open 24/7.", "WARNINGS", "Critical", null, "ALERT", "/citizen-dashboard.html");
            createNotification(cleanEmail, "Citizen", "Community Safety Advisory", "Emergency relief supplies and medical assistance available at Camp B.", "MESSAGES", "Low", null, "ALERT", "/citizen-dashboard.html");
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
