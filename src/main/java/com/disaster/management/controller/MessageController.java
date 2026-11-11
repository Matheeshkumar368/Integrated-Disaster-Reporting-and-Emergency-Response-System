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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Message;
import com.disaster.management.service.MessageService;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081", "*"})
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageService messageService;

    private Authentication getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        return auth;
    }

    private String extractRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("Citizen");
    }

    // Send an authenticated message adhering to role-based communication matrix
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

    // Get conversation history with a target user (query param: ?with=...)
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

    // Get conversation history with path variable
    @GetMapping("/conversation/{otherEmail}")
    public ResponseEntity<?> getConversationByPath(@PathVariable String otherEmail) {
        return getConversation(otherEmail);
    }

    // Inspect conversation thread between two users (e.g., user1 and user2)
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

    // Get permitted contacts for authenticated user
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

    // Get messages sent by authenticated user
    @GetMapping("/sent")
    public ResponseEntity<List<Message>> getSentMessages() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(messageService.getSentMessages(auth.getName()));
    }

    // Get messages received by authenticated user
    @GetMapping("/received")
    public ResponseEntity<List<Message>> getReceivedMessages() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(messageService.getReceivedMessages(auth.getName()));
    }

    // Get broadcast messages for Responders
    @GetMapping("/responders")
    public ResponseEntity<List<Message>> getMessagesForResponders() {
        Authentication auth = getAuthenticatedUser();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(messageService.getMessagesForResponders());
    }
}
