package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.Message;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.MessageRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CitizenReportRepository citizenReportRepository;

    @Autowired
    private RescueTaskRepository rescueTaskRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    /**
     * Normalize role strings: strips ROLE_ prefix, trims, and normalizes capitalization.
     */
    public String normalizeRole(String role) {
        if (role == null) return "Citizen";
        String r = role.replace("ROLE_", "").trim();
        if ("ADMIN".equalsIgnoreCase(r)) return "Admin";
        if ("RESPONDER".equalsIgnoreCase(r)) return "Responder";
        return "Citizen";
    }

    /**
     * Check if a user is an Admin.
     */
    public boolean isAdmin(String email) {
        if (email == null) return false;
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(u -> "Admin".equalsIgnoreCase(normalizeRole(u.getRole())))
                .orElse(false);
    }

    /**
     * Verify whether a Responder is assigned to a Citizen through CitizenReports or RescueTasks.
     */
    public boolean isResponderAssignedToCitizen(String citizenEmail, String responderEmail) {
        if (citizenEmail == null || responderEmail == null) return false;
        String cEmail = citizenEmail.trim().toLowerCase();
        String rEmail = responderEmail.trim().toLowerCase();

        // 1. Direct assignment in active or historic CitizenReports
        List<CitizenReport> reports = citizenReportRepository.findByCitizenEmailAndIsDeletedFalseOrderByTimestampDesc(cEmail);
        for (CitizenReport r : reports) {
            if (r.getAssignedResponderEmail() != null && r.getAssignedResponderEmail().trim().equalsIgnoreCase(rEmail)) {
                return true;
            }
        }

        List<CitizenReport> allReports = citizenReportRepository.findByCitizenEmailOrderByTimestampDesc(cEmail);
        for (CitizenReport r : allReports) {
            if (r.getAssignedResponderEmail() != null && r.getAssignedResponderEmail().trim().equalsIgnoreCase(rEmail)) {
                return true;
            }
        }

        // 2. Assignment through RescueTask linked to a CitizenReport
        List<RescueTask> tasks = rescueTaskRepository.findByAssignedTo(rEmail);
        for (RescueTask t : tasks) {
            if (t.getCitizenReportId() != null) {
                Optional<CitizenReport> cr = citizenReportRepository.findById(t.getCitizenReportId());
                if (cr.isPresent()) {
                    String repCitizen = cr.get().getCitizenEmail();
                    if (repCitizen != null && repCitizen.trim().equalsIgnoreCase(cEmail)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Validate communication authorization and send a message.
     */
    @Transactional
    public Message sendMessage(String authEmail, String authRole, Message messageInput) {
        if (authEmail == null || authEmail.trim().isEmpty()) {
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }
        if (messageInput == null) {
            throw new IllegalArgumentException("Message payload cannot be empty.");
        }

        String rawBody = messageInput.getMessageBody() != null ? messageInput.getMessageBody() : messageInput.getMessage();
        if (rawBody == null || rawBody.trim().isEmpty()) {
            throw new IllegalArgumentException("Message text cannot be empty.");
        }

        String targetRecipient = messageInput.getRecipientEmail() != null ? messageInput.getRecipientEmail() : messageInput.getReceiverEmail();
        if (targetRecipient == null || targetRecipient.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }

        String senderEmail = authEmail.trim().toLowerCase();
        String receiverEmail = targetRecipient.trim().toLowerCase();
        String senderRole = normalizeRole(authRole);

        User receiverUser = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() -> new IllegalArgumentException("Recipient user with email " + receiverEmail + " not found."));

        String receiverRole = normalizeRole(receiverUser.getRole());

        // Enforce role-based communication matrix
        if ("Admin".equalsIgnoreCase(senderRole)) {
            // Admin can send messages to any user (Citizen, Responder, Admin)
            log.info("Admin [{}] sending message to [{}] ({})", senderEmail, receiverEmail, receiverRole);
        } else if ("Citizen".equalsIgnoreCase(senderRole)) {
            if ("Admin".equalsIgnoreCase(receiverRole)) {
                // Citizen can contact Admin (help / support)
                log.info("Citizen [{}] sending support message to Admin [{}]", senderEmail, receiverEmail);
            } else if ("Responder".equalsIgnoreCase(receiverRole)) {
                // Citizen can only contact an ASSIGNED Responder
                boolean isAssigned = isResponderAssignedToCitizen(senderEmail, receiverEmail);
                if (!isAssigned) {
                    log.warn("Access Denied: Citizen [{}] tried to message unassigned responder [{}]", senderEmail, receiverEmail);
                    throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
                }
                log.info("Citizen [{}] sending message to assigned Responder [{}]", senderEmail, receiverEmail);
            } else {
                // Citizen cannot message other citizens directly
                log.warn("Access Denied: Citizen [{}] tried to message another citizen [{}]", senderEmail, receiverEmail);
                throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
            }
        } else if ("Responder".equalsIgnoreCase(senderRole)) {
            if ("Admin".equalsIgnoreCase(receiverRole)) {
                // Responder can contact Admin (Mission Control)
                log.info("Responder [{}] sending message to Admin [{}]", senderEmail, receiverEmail);
            } else if ("Citizen".equalsIgnoreCase(receiverRole)) {
                // Responder can only contact an ASSIGNED Citizen
                boolean isAssigned = isResponderAssignedToCitizen(receiverEmail, senderEmail);
                if (!isAssigned) {
                    log.warn("Access Denied: Responder [{}] tried to message unassigned citizen [{}]", senderEmail, receiverEmail);
                    throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
                }
                log.info("Responder [{}] replying to assigned Citizen [{}]", senderEmail, receiverEmail);
            } else {
                // Responder cannot message unrelated responders directly
                log.warn("Access Denied: Responder [{}] tried to message another responder [{}]", senderEmail, receiverEmail);
                throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
            }
        } else {
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }

        // Build and persist message
        Message msg = new Message();
        msg.setSenderEmail(senderEmail);
        msg.setSenderRole(senderRole);
        userRepository.findByEmail(senderEmail).ifPresent(u -> msg.setSenderName(u.getName()));
        msg.setRecipientEmail(receiverEmail);
        msg.setRecipientRole(receiverRole);

        String subject = messageInput.getSubject();
        if (subject == null || subject.trim().isEmpty()) {
            subject = "Direct Communication";
        }
        msg.setSubject(subject.trim());
        msg.setMessageBody(rawBody.trim());
        msg.setRelatedAlertId(messageInput.getRelatedAlertId());
        msg.setCreatedAt(LocalDateTime.now());
        msg.setMessageStatus("Sent");
        msg.setIsDeletedBySender(false);
        msg.setIsDeletedByRecipient(false);

        Message saved = messageRepository.save(msg);
        log.info("Message #{} dispatched successfully from [{}] to [{}] with subject [{}]", saved.getId(), senderEmail, receiverEmail, saved.getSubject());

        if (notificationService != null) {
            try {
                String senderDisplayName = msg.getSenderName() != null ? msg.getSenderName() : senderEmail;
                String targetPage = "admin-messages.html";
                if ("Citizen".equalsIgnoreCase(receiverRole)) targetPage = "citizen-messages.html";
                else if ("Responder".equalsIgnoreCase(receiverRole)) targetPage = "responder-messages.html";

                String notifTitle = (senderRole.equalsIgnoreCase("Admin") ? "Admin Message: " : "Message from ") 
                        + senderDisplayName + " — " + saved.getSubject();

                notificationService.createNotification(
                    receiverEmail,
                    receiverRole,
                    notifTitle,
                    saved.getMessageBody(),
                    "DIRECT_MESSAGE",
                    "Medium",
                    saved.getId(),
                    "MESSAGE",
                    targetPage
                );
            } catch (Exception e) {
                log.warn("Notification dispatch warning on message send: {}", e.getMessage());
            }
        }

        return saved;
    }

    /**
     * Retrieve inbox messages for authenticated user.
     */
    public List<Message> getInbox(String authEmail) {
        if (authEmail == null || authEmail.trim().isEmpty()) return Collections.emptyList();
        return messageRepository.findInbox(authEmail.trim().toLowerCase());
    }

    /**
     * Retrieve sent messages for authenticated user.
     */
    public List<Message> getSent(String authEmail) {
        if (authEmail == null || authEmail.trim().isEmpty()) return Collections.emptyList();
        return messageRepository.findSent(authEmail.trim().toLowerCase());
    }

    /**
     * Retrieve two-way conversation between authenticated caller and target otherEmail.
     * Enforces privacy and role-based conversation access.
     */
    @Transactional
    public List<Message> getConversation(String authEmail, String authRole, String otherEmail) {
        if (authEmail == null || otherEmail == null || otherEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Target email cannot be empty.");
        }

        String callerEmail = authEmail.trim().toLowerCase();
        String targetEmail = otherEmail.trim().toLowerCase();
        String callerRole = normalizeRole(authRole);

        User targetUser = userRepository.findByEmail(targetEmail)
                .orElseThrow(() -> new IllegalArgumentException("Target user with email " + targetEmail + " not found."));

        String targetRole = normalizeRole(targetUser.getRole());

        // Verify conversation view privilege
        if ("Admin".equalsIgnoreCase(callerRole)) {
            // Admin can view conversation with any user
        } else if ("Citizen".equalsIgnoreCase(callerRole)) {
            if ("Admin".equalsIgnoreCase(targetRole)) {
                // Allowed to view thread with Admin
            } else if ("Responder".equalsIgnoreCase(targetRole)) {
                boolean isAssigned = isResponderAssignedToCitizen(callerEmail, targetEmail);
                if (!isAssigned) {
                    log.warn("Access Denied: Citizen [{}] tried to view conversation of unassigned responder [{}]", callerEmail, targetEmail);
                    throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
                }
            } else {
                log.warn("Access Denied: Citizen [{}] tried to view conversation with citizen [{}]", callerEmail, targetEmail);
                throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
            }
        } else if ("Responder".equalsIgnoreCase(callerRole)) {
            if ("Admin".equalsIgnoreCase(targetRole)) {
                // Allowed to view thread with Admin
            } else if ("Citizen".equalsIgnoreCase(targetRole)) {
                boolean isAssigned = isResponderAssignedToCitizen(targetEmail, callerEmail);
                if (!isAssigned) {
                    log.warn("Access Denied: Responder [{}] tried to view conversation with unassigned citizen [{}]", callerEmail, targetEmail);
                    throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
                }
            } else {
                log.warn("Access Denied: Responder [{}] tried to view conversation with responder [{}]", callerEmail, targetEmail);
                throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
            }
        } else {
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }

        // Mark incoming messages as read
        messageRepository.markConversationAsRead(callerEmail, targetEmail);

        return messageRepository.findConversationBetween(callerEmail, targetEmail);
    }

    /**
     * Mark a single message as read.
     */
    @Transactional
    public Message markAsRead(Long id, String authEmail) {
        if (id == null || authEmail == null) {
            throw new IllegalArgumentException("Message ID and user email required.");
        }
        Message msg = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!authEmail.trim().equalsIgnoreCase(msg.getRecipientEmail())) {
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }

        msg.setMessageStatus("Read");
        msg.setReadAt(LocalDateTime.now());
        return messageRepository.save(msg);
    }

    /**
     * Soft delete a message for sender or recipient.
     */
    @Transactional
    public void deleteMessage(Long id, String authEmail) {
        if (id == null || authEmail == null) {
            throw new IllegalArgumentException("Message ID and user email required.");
        }
        Message msg = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        String email = authEmail.trim().toLowerCase();
        boolean isSender = email.equalsIgnoreCase(msg.getSenderEmail());
        boolean isRecipient = email.equalsIgnoreCase(msg.getRecipientEmail());

        if (!isSender && !isRecipient && !isAdmin(authEmail)) {
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }

        if (isSender) {
            msg.setIsDeletedBySender(true);
        }
        if (isRecipient) {
            msg.setIsDeletedByRecipient(true);
        }
        if (Boolean.TRUE.equals(msg.getIsDeletedBySender()) && Boolean.TRUE.equals(msg.getIsDeletedByRecipient())) {
            messageRepository.delete(msg);
        } else {
            messageRepository.save(msg);
        }
    }

    /**
     * Get count of unread messages for authenticated user.
     */
    public long getUnreadCount(String authEmail) {
        if (authEmail == null || authEmail.trim().isEmpty()) return 0;
        return messageRepository.countUnreadMessages(authEmail.trim().toLowerCase());
    }

    /**
     * Retrieve thread between user1 and user2.
     * Prevents any user from viewing third-party conversations unless Admin.
     */
    @Transactional
    public List<Message> getThread(String authEmail, String authRole, String user1, String user2) {
        if (authEmail == null || user1 == null || user2 == null) {
            throw new IllegalArgumentException("User parameters cannot be null.");
        }

        String callerEmail = authEmail.trim().toLowerCase();
        String callerRole = normalizeRole(authRole);
        String u1 = user1.trim().toLowerCase();
        String u2 = user2.trim().toLowerCase();

        if ("Admin".equalsIgnoreCase(callerRole)) {
            // Admin can inspect any thread for coordination
            return messageRepository.findConversationBetween(u1, u2);
        }

        // Non-admin MUST be a participant in the conversation
        if (!callerEmail.equalsIgnoreCase(u1) && !callerEmail.equalsIgnoreCase(u2)) {
            log.warn("Access Denied: User [{}] attempted to view private conversation between [{}] and [{}]", callerEmail, u1, u2);
            throw new AccessDeniedException("Forbidden: Insufficient privileges for this resource");
        }

        String otherParticipant = callerEmail.equalsIgnoreCase(u1) ? u2 : u1;
        return getConversation(callerEmail, callerRole, otherParticipant);
    }

    /**
     * Get sent messages for authenticated user (legacy).
     */
    public List<Message> getSentMessages(String authEmail) {
        return getSent(authEmail);
    }

    /**
     * Get received messages for authenticated user (legacy).
     */
    public List<Message> getReceivedMessages(String authEmail) {
        return getInbox(authEmail);
    }

    /**
     * Get broadcast messages for Responders.
     */
    public List<Message> getMessagesForResponders() {
        return messageRepository.findByRecipientRoleOrderByCreatedAtDesc("Responder");
    }

    /**
     * Get permitted contacts list for the authenticated user based on role and assignment status.
     */
    public List<Map<String, Object>> getPermittedContacts(String authEmail, String authRole) {
        if (authEmail == null) return Collections.emptyList();
        String callerEmail = authEmail.trim().toLowerCase();
        String callerRole = normalizeRole(authRole);

        List<Map<String, Object>> contacts = new ArrayList<>();
        Set<String> addedEmails = new HashSet<>();

        if ("Admin".equalsIgnoreCase(callerRole)) {
            // Admin can contact all Citizens and all Responders
            List<User> citizens = userRepository.findByRole("Citizen");
            List<User> responders = userRepository.findByRole("Responder");

            for (User r : responders) {
                if (addedEmails.add(r.getEmail().toLowerCase())) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("email", r.getEmail());
                    map.put("name", r.getName() != null ? r.getName() : r.getEmail());
                    map.put("role", "Responder");
                    map.put("badge", "Field Responder");
                    contacts.add(map);
                }
            }
            for (User c : citizens) {
                if (addedEmails.add(c.getEmail().toLowerCase())) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("email", c.getEmail());
                    map.put("name", c.getName() != null ? c.getName() : c.getEmail());
                    map.put("role", "Citizen");
                    map.put("badge", "Citizen");
                    contacts.add(map);
                }
            }
        } else if ("Citizen".equalsIgnoreCase(callerRole)) {
            // 1. All Admins (Mission Control)
            List<User> admins = userRepository.findByRole("Admin");
            for (User a : admins) {
                if (addedEmails.add(a.getEmail().toLowerCase())) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("email", a.getEmail());
                    map.put("name", a.getName() != null ? a.getName() : "Emergency Command Center");
                    map.put("role", "Admin");
                    map.put("badge", "Command Control");
                    contacts.add(map);
                }
            }

            // 2. Assigned Responders only
            List<CitizenReport> reports = citizenReportRepository.findByCitizenEmailAndIsDeletedFalseOrderByTimestampDesc(callerEmail);
            for (CitizenReport rep : reports) {
                if (rep.getAssignedResponderEmail() != null) {
                    String rEmail = rep.getAssignedResponderEmail().trim().toLowerCase();
                    if (addedEmails.add(rEmail)) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("email", rEmail);
                        map.put("name", rep.getAssignedResponderName() != null ? rep.getAssignedResponderName() : rEmail);
                        map.put("role", "Responder");
                        map.put("badge", "Assigned Unit (Report #" + rep.getReportId() + ")");
                        contacts.add(map);
                    }
                }
            }
        } else if ("Responder".equalsIgnoreCase(callerRole)) {
            // 1. All Admins (Mission Control)
            List<User> admins = userRepository.findByRole("Admin");
            for (User a : admins) {
                if (addedEmails.add(a.getEmail().toLowerCase())) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("email", a.getEmail());
                    map.put("name", a.getName() != null ? a.getName() : "Emergency Command Center");
                    map.put("role", "Admin");
                    map.put("badge", "Command Control");
                    contacts.add(map);
                }
            }

            // 2. Assigned Citizens from tasks and reports
            List<RescueTask> tasks = rescueTaskRepository.findByAssignedTo(callerEmail);
            for (RescueTask task : tasks) {
                if (task.getCitizenReportId() != null) {
                    citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(cr -> {
                        if (cr.getCitizenEmail() != null) {
                            String cEmail = cr.getCitizenEmail().trim().toLowerCase();
                            if (addedEmails.add(cEmail)) {
                                Map<String, Object> map = new LinkedHashMap<>();
                                map.put("email", cEmail);
                                userRepository.findByEmail(cEmail).ifPresent(u -> map.put("name", u.getName()));
                                if (!map.containsKey("name")) map.put("name", cEmail);
                                map.put("role", "Citizen");
                                map.put("badge", "Assigned Citizen (Task #" + task.getTaskId() + ")");
                                contacts.add(map);
                            }
                        }
                    });
                }
            }
        }

        return contacts;
    }
}
