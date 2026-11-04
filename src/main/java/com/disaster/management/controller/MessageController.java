package com.disaster.management.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Message;
import com.disaster.management.entity.User;
import com.disaster.management.repository.MessageRepository;
import com.disaster.management.repository.UserRepository;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081"})
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    // Send an in-app emergency message / notification
    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Message message) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Authentication required"));
        }

        String email = auth.getName();
        String role = auth.getAuthorities().stream()
            .findFirst()
            .map(a -> a.getAuthority().replace("ROLE_", ""))
            .orElse("Citizen");

        try {
            message.setSenderEmail(email);
            message.setSenderRole(role);
            userRepository.findByEmail(email).ifPresent(u -> message.setSenderName(u.getName()));
            message.setSentAt(LocalDateTime.now());
            if (message.getStatus() == null || message.getStatus().trim().isEmpty()) {
                message.setStatus("Sent");
            }

            Message savedMessage = messageRepository.save(message);
            return ResponseEntity.ok(savedMessage);
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Error sending message: " + e.getMessage()));
        }
    }

    // Get messages sent by authenticated user
    @GetMapping("/sent")
    public ResponseEntity<List<Message>> getSentMessages() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Message> messages = messageRepository.findBySenderEmailOrderBySentAtDesc(auth.getName());
        return ResponseEntity.ok(messages);
    }

    // Get messages received by authenticated user
    @GetMapping("/received")
    public ResponseEntity<List<Message>> getReceivedMessages() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Message> messages = messageRepository.findByReceiverEmailOrderBySentAtDesc(auth.getName());
        return ResponseEntity.ok(messages);
    }

    // Get broadcast messages for Responders
    @GetMapping("/responders")
    public ResponseEntity<List<Message>> getMessagesForResponders() {
        List<Message> messages = messageRepository.findByReceiverRoleOrderBySentAtDesc("Responder");
        return ResponseEntity.ok(messages);
    }
}
