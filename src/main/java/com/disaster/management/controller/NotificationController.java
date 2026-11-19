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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Notification;
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

    // Get notifications for current user with optional filtering
    @GetMapping
    public ResponseEntity<?> getNotifications(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {

        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            List<Notification> list = notificationService.getNotificationsForUser(auth.getName(), category, status);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("Failed to fetch notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving notifications: " + e.getMessage()));
        }
    }

    // Get unread and total count for badge rendering
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            Map<String, Object> summary = notificationService.getNotificationSummary(auth.getName());
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to fetch notification counts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving counts: " + e.getMessage()));
        }
    }

    // Mark single notification as read
    @PutMapping("/{id}/read")
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
    @PutMapping("/read-all")
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
    @DeleteMapping("/{id}")
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
