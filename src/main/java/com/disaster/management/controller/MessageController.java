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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.security.RoleUtil;

import com.disaster.management.entity.Message;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.service.MessageService;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081", "*"})
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    private Authentication getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        return auth;
    }

    private String extractRole(Authentication auth) {
        String role = RoleUtil.getRole(auth);
        if (role != null) return role;
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("Citizen");
    }

    // ─────────────────────────────────────────────────────────────
    // 1. POST /api/messages - Send authenticated message
    // ─────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Message message) {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        String email = auth.getName();
        String role = extractRole(auth);

        try {
            Message savedMessage = messageService.sendMessage(email, role, message);
            return ResponseEntity.ok(savedMessage);
        } catch (AccessDeniedException e) {
            log.warn("AccessDenied in sendMessage: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for this resource"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error sending message: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. GET /api/messages/inbox - Get received messages
    // ─────────────────────────────────────────────────────────────
    @GetMapping({"/inbox", "/received"})
    public ResponseEntity<?> getInbox() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        List<Message> inbox = messageService.getInbox(auth.getName());
        return ResponseEntity.ok(inbox);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET /api/messages/sent - Get sent messages
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/sent")
    public ResponseEntity<?> getSentMessages() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        List<Message> sent = messageService.getSent(auth.getName());
        return ResponseEntity.ok(sent);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. GET /api/messages/conversation?with=... OR /conversation/{target}
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversation")
    public ResponseEntity<?> getConversation(@RequestParam("with") String withEmail) {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        String email = auth.getName();
        String role = extractRole(auth);

        try {
            List<Message> conversation = messageService.getConversation(email, role, withEmail);
            return ResponseEntity.ok(conversation);
        } catch (AccessDeniedException e) {
            log.warn("AccessDenied in getConversation with {}: {}", withEmail, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for this resource"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get conversation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving conversation: " + e.getMessage()));
        }
    }

    @GetMapping("/conversation/{otherEmailOrId}")
    public ResponseEntity<?> getConversationByPath(@PathVariable String otherEmailOrId) {
        String targetEmail = otherEmailOrId;
        if (otherEmailOrId != null && otherEmailOrId.matches("\\d+")) {
            try {
                targetEmail = userRepository.findById(Long.parseLong(otherEmailOrId))
                        .map(User::getEmail)
                        .orElse(otherEmailOrId);
            } catch (Exception ignored) {}
        }
        return getConversation(targetEmail);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. PATCH & PUT /api/messages/{id}/read - Mark single message read
    // ─────────────────────────────────────────────────────────────
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markMessageReadPatch(@PathVariable Long id) {
        return markMessageReadInternal(id);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markMessageReadPut(@PathVariable Long id) {
        return markMessageReadInternal(id);
    }

    private ResponseEntity<?> markMessageReadInternal(Long id) {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            Message updated = messageService.markAsRead(id, auth.getName());
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for this resource"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error updating message: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. DELETE /api/messages/{id} - Delete message
    // ─────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id) {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        try {
            messageService.deleteMessage(id, auth.getName());
            return ResponseEntity.ok(Map.of("status", "success", "message", "Message deleted successfully."));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for this resource"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error deleting message: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. GET /api/messages/unread-count - Unread message counter
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        long unread = messageService.getUnreadCount(auth.getName());
        return ResponseEntity.ok(Map.of("unreadCount", unread));
    }

    // ─────────────────────────────────────────────────────────────
    // 8. GET /api/messages/contacts - Permitted communication contacts
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/contacts")
    public ResponseEntity<?> getPermittedContacts() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        String email = auth.getName();
        String role = extractRole(auth);

        try {
            List<Map<String, Object>> contacts = messageService.getPermittedContacts(email, role);
            return ResponseEntity.ok(contacts);
        } catch (Exception e) {
            log.error("Failed to get contacts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving contacts: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. GET /api/messages/thread - Thread between two users
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/thread")
    public ResponseEntity<?> getThread(
            @RequestParam("user1") String user1,
            @RequestParam("user2") String user2) {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized: Authentication token is missing or invalid"));
        }

        String email = auth.getName();
        String role = extractRole(auth);

        try {
            List<Message> thread = messageService.getThread(email, role, user1, user2);
            return ResponseEntity.ok(thread);
        } catch (AccessDeniedException e) {
            log.warn("AccessDenied in getThread [{} <-> {}] by user [{}]: {}", user1, user2, email, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "error", "message", "Forbidden: Insufficient privileges for this resource"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get thread: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Error retrieving thread: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 10. GET /api/messages/responders - Legacy responder broadcasts
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/responders")
    public ResponseEntity<List<Message>> getMessagesForResponders() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(messageService.getMessagesForResponders());
    }
}
