package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE LOWER(m.recipientEmail) = LOWER(:recipientEmail) AND (m.isDeletedByRecipient = false OR m.isDeletedByRecipient IS NULL) ORDER BY m.createdAt DESC")
    List<Message> findInbox(@Param("recipientEmail") String recipientEmail);

    @Query("SELECT m FROM Message m WHERE LOWER(m.senderEmail) = LOWER(:senderEmail) AND (m.isDeletedBySender = false OR m.isDeletedBySender IS NULL) ORDER BY m.createdAt DESC")
    List<Message> findSent(@Param("senderEmail") String senderEmail);

    List<Message> findBySenderEmailOrderByCreatedAtDesc(String senderEmail);

    List<Message> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    List<Message> findByRecipientRoleOrderByCreatedAtDesc(String recipientRole);

    List<Message> findByRelatedAlertIdOrderByCreatedAtDesc(Long alertId);

    @Query("SELECT m FROM Message m WHERE " +
           "((LOWER(m.senderEmail) = LOWER(:user1) AND LOWER(m.recipientEmail) = LOWER(:user2) AND (m.isDeletedBySender = false OR m.isDeletedBySender IS NULL)) OR " +
           "(LOWER(m.senderEmail) = LOWER(:user2) AND LOWER(m.recipientEmail) = LOWER(:user1) AND (m.isDeletedByRecipient = false OR m.isDeletedByRecipient IS NULL))) " +
           "ORDER BY m.createdAt ASC")
    List<Message> findConversationBetween(@Param("user1") String user1, @Param("user2") String user2);

    @Query("SELECT m FROM Message m WHERE (LOWER(m.senderEmail) = LOWER(:email) AND (m.isDeletedBySender = false OR m.isDeletedBySender IS NULL)) OR (LOWER(m.recipientEmail) = LOWER(:email) AND (m.isDeletedByRecipient = false OR m.isDeletedByRecipient IS NULL)) ORDER BY m.createdAt DESC")
    List<Message> findAllInvolvingUser(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.messageStatus = 'Read', m.readAt = CURRENT_TIMESTAMP WHERE LOWER(m.recipientEmail) = LOWER(:recipientEmail) AND LOWER(m.senderEmail) = LOWER(:senderEmail) AND (m.messageStatus IS NULL OR m.messageStatus != 'Read')")
    int markConversationAsRead(@Param("recipientEmail") String recipientEmail, @Param("senderEmail") String senderEmail);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.messageStatus = 'Read', m.readAt = CURRENT_TIMESTAMP WHERE m.id = :id AND LOWER(m.recipientEmail) = LOWER(:recipientEmail)")
    int markMessageAsRead(@Param("id") Long id, @Param("recipientEmail") String recipientEmail);

    @Query("SELECT COUNT(m) FROM Message m WHERE LOWER(m.recipientEmail) = LOWER(:email) AND (m.messageStatus IS NULL OR m.messageStatus != 'Read') AND (m.isDeletedByRecipient = false OR m.isDeletedByRecipient IS NULL)")
    long countUnreadMessages(@Param("email") String email);
}
