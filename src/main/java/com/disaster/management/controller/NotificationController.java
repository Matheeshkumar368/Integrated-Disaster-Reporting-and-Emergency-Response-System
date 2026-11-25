package com.disaster.management.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Notification;
import com.disaster.management.security.RoleUtil;
import com.disaster.management.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081", "*"})
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    private NotificationService notificationService;

    private Authentication getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        return auth;
    }

    // Role-specific endpoint: Admin Command Notifications
    @GetMapping("/admin")
    public ResponseEntity<?> getAdminNotifications(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {

        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        if (!RoleUtil.isAdmin(auth)) {
            log.warn("Unauthorized attempt to access Admin notifications by user [{}] with authorities {}", auth.getName(), auth.getAuthorities());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Admin notification feed"));
        }

        try {
            List<Notification> list = notificationService.getAdminNotifications(auth.getName(), category, status);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("Failed to fetch Admin notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving Admin notifications: " + e.getMessage()));
        }
    }

    // Role-specific endpoint: Citizen Safety Notifications
    @GetMapping("/citizen")
    public ResponseEntity<?> getCitizenNotifications(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {

        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        if (!RoleUtil.isCitizen(auth)) {
            log.warn("Unauthorized attempt to access Citizen notifications by user [{}] with authorities {}", auth.getName(), auth.getAuthorities());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Citizen notification feed"));
        }

        try {
            List<Notification> list = notificationService.getCitizenNotifications(auth.getName(), category, status);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("Failed to fetch Citizen notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving Citizen notifications: " + e.getMessage()));
        }
    }

    // Role-specific endpoint: Responder Field Tactical Notifications
    @GetMapping("/responder")
    public ResponseEntity<?> getResponderNotifications(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {

        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        if (!RoleUtil.isResponder(auth)) {
            log.warn("Unauthorized attempt to access Responder notifications by user [{}] with authorities {}", auth.getName(), auth.getAuthorities());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Responder notification feed"));
        }

        try {
            List<Notification> list = notificationService.getResponderNotifications(auth.getName(), category, status);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("Failed to fetch Responder notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving Responder notifications: " + e.getMessage()));
        }
    }

    // Role-specific unread count: Admin
    @GetMapping({"/admin/unread-count", "/admin/summary"})
    public ResponseEntity<?> getAdminUnreadCount() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }
        if (!RoleUtil.isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Admin summary"));
        }
        return ResponseEntity.ok(notificationService.getRoleNotificationSummary(auth.getName(), "Admin"));
    }

    // Role-specific unread count: Citizen
    @GetMapping({"/citizen/unread-count", "/citizen/summary"})
    public ResponseEntity<?> getCitizenUnreadCount() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }
        if (!RoleUtil.isCitizen(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Citizen summary"));
        }
        return ResponseEntity.ok(notificationService.getRoleNotificationSummary(auth.getName(), "Citizen"));
    }

    // Role-specific unread count: Responder
    @GetMapping({"/responder/unread-count", "/responder/summary"})
    public ResponseEntity<?> getResponderUnreadCount() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }
        if (!RoleUtil.isResponder(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Responder summary"));
        }
        return ResponseEntity.ok(notificationService.getRoleNotificationSummary(auth.getName(), "Responder"));
    }

    // Role-specific Mark All as Read: Admin
    @PutMapping({"/admin/read-all", "/admin/mark-all-read"})
    public ResponseEntity<?> markAllAsReadAdmin() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }
        if (!RoleUtil.isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Admin action"));
        }
        int count = notificationService.markAllAsReadForRole(auth.getName(), "Admin");
        return ResponseEntity.ok(Map.of("status", "success", "updatedCount", count));
    }

    // Role-specific Mark All as Read: Citizen
    @PutMapping({"/citizen/read-all", "/citizen/mark-all-read"})
    public ResponseEntity<?> markAllAsReadCitizen() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }
        if (!RoleUtil.isCitizen(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Citizen action"));
        }
        int count = notificationService.markAllAsReadForRole(auth.getName(), "Citizen");
        return ResponseEntity.ok(Map.of("status", "success", "updatedCount", count));
    }

    // Role-specific Mark All as Read: Responder
    @PutMapping({"/responder/read-all", "/responder/mark-all-read"})
    public ResponseEntity<?> markAllAsReadResponder() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }
        if (!RoleUtil.isResponder(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for Responder action"));
        }
        int count = notificationService.markAllAsReadForRole(auth.getName(), "Responder");
        return ResponseEntity.ok(Map.of("status", "success", "updatedCount", count));
    }

    // General / Legacy endpoint: routes to role-specific feed based on authenticated principal
    @GetMapping({"", "/inbox"})
    public ResponseEntity<?> getNotifications(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {

        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            List<Notification> list;
            if (RoleUtil.isAdmin(auth)) {
                list = notificationService.getAdminNotifications(auth.getName(), category, status);
            } else if (RoleUtil.isResponder(auth)) {
                list = notificationService.getResponderNotifications(auth.getName(), category, status);
            } else {
                list = notificationService.getCitizenNotifications(auth.getName(), category, status);
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("Failed to fetch notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving notifications: " + e.getMessage()));
        }
    }

    // General / Legacy unread count: calculates role-scoped count
    @GetMapping({"/unread-count", "/summary"})
    public ResponseEntity<?> getUnreadCount() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            String role = RoleUtil.isAdmin(auth) ? "Admin" : (RoleUtil.isResponder(auth) ? "Responder" : "Citizen");
            Map<String, Object> summary = notificationService.getRoleNotificationSummary(auth.getName(), role);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to fetch notification counts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving counts: " + e.getMessage()));
        }
    }

    // Mark single notification as read (supports both general and role-scoped paths)
    @RequestMapping(value = {"/{id}/read", "/{id}/mark-read", "/admin/{id}/read", "/citizen/{id}/read", "/responder/{id}/read"}, method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            Notification updated = notificationService.markAsRead(id, auth.getName());
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for this resource"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to mark notification as read: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error updating notification: " + e.getMessage()));
        }
    }

    // Mark all notifications as read for current user
    @PutMapping({"/read-all", "/mark-all-read"})
    public ResponseEntity<?> markAllAsRead() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            int count = notificationService.markAllAsRead(auth.getName());
            return ResponseEntity.ok(Map.of("status", "success", "updatedCount", count));
        } catch (Exception e) {
            log.error("Failed to mark all as read: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error updating notifications: " + e.getMessage()));
        }
    }

    // Delete notification from personal notification list
    @DeleteMapping({"/{id}", "/admin/{id}", "/citizen/{id}", "/responder/{id}"})
    public ResponseEntity<?> deleteNotification(@PathVariable Long id) {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            notificationService.deleteNotification(id, auth.getName());
            return ResponseEntity.ok(Map.of("status", "success", "message", "Notification removed from personal list"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for this resource"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete notification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error deleting notification: " + e.getMessage()));
        }
    }
}
