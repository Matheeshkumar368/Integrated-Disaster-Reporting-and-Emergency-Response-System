package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    List<Message> findBySenderEmailOrderBySentAtDesc(String senderEmail);
    
    List<Message> findByReceiverEmailOrderBySentAtDesc(String receiverEmail);
    
    List<Message> findByReceiverRoleOrderBySentAtDesc(String receiverRole);
    
    List<Message> findByRelatedAlertIdOrderBySentAtDesc(Long alertId);
}
