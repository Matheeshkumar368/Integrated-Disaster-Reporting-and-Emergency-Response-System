package com.disaster.management.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Message;
import com.disaster.management.repository.MessageRepository;
import com.disaster.management.security.JwtUtil;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> sendMessage(
            @RequestBody Message message,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            
            message.setSenderEmail(email);
            message.setSenderRole(role);
            message.setSentAt(java.time.LocalDateTime.now());
            
            Message savedMessage = messageRepository.save(message);
            return ResponseEntity.ok(savedMessage);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error sending message: " + e.getMessage());
        }
    }

    @GetMapping("/sent")
    public ResponseEntity<List<Message>> getSentMessages(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String email = jwtUtil.extractEmail(token);
            
            List<Message> messages = messageRepository.findBySenderEmailOrderBySentAtDesc(email);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/received")
    public ResponseEntity<List<Message>> getReceivedMessages(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String email = jwtUtil.extractEmail(token);
            
            List<Message> messages = messageRepository.findByReceiverEmailOrderBySentAtDesc(email);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/responders")
    public ResponseEntity<?> getMessagesForResponders(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        // Allow responders to see all messages sent to them
        try {
            List<Message> messages = messageRepository.findByReceiverRoleOrderBySentAtDesc("Responder");
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
