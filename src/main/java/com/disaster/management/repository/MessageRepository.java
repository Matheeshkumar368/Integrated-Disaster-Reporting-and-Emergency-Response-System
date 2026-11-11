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
    
    List<Message> findBySenderEmailOrderBySentAtDesc(String senderEmail);
    
    List<Message> findByReceiverEmailOrderBySentAtDesc(String receiverEmail);
    
    List<Message> findByReceiverRoleOrderBySentAtDesc(String receiverRole);
    
    List<Message> findByRelatedAlertIdOrderBySentAtDesc(Long alertId);

    @Query("SELECT m FROM Message m WHERE " +
           "(LOWER(m.senderEmail) = LOWER(:user1) AND LOWER(m.receiverEmail) = LOWER(:user2)) OR " +
           "(LOWER(m.senderEmail) = LOWER(:user2) AND LOWER(m.receiverEmail) = LOWER(:user1)) " +
           "ORDER BY m.sentAt ASC")
    List<Message> findConversationBetween(@Param("user1") String user1, @Param("user2") String user2);

    @Query("SELECT m FROM Message m WHERE LOWER(m.senderEmail) = LOWER(:email) OR LOWER(m.receiverEmail) = LOWER(:email) ORDER BY m.sentAt DESC")
    List<Message> findAllInvolvingUser(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = 'Read' WHERE LOWER(m.receiverEmail) = LOWER(:receiverEmail) AND LOWER(m.senderEmail) = LOWER(:senderEmail) AND (m.status IS NULL OR m.status != 'Read')")
    int markConversationAsRead(@Param("receiverEmail") String receiverEmail, @Param("senderEmail") String senderEmail);

    @Query("SELECT COUNT(m) FROM Message m WHERE LOWER(m.receiverEmail) = LOWER(:email) AND (m.status IS NULL OR m.status != 'Read')")
    long countUnreadMessages(@Param("email") String email);
}
