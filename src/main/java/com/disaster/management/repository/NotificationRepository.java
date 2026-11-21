package com.disaster.management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientEmailAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(String recipientEmail);

    List<Notification> findByRecipientEmailAndCategoryIgnoreCaseAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(String recipientEmail, String category);

    List<Notification> findByRecipientEmailAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(String recipientEmail, Boolean isRead);

    List<Notification> findByRecipientEmailAndCategoryIgnoreCaseAndIsReadAndIsDeletedByRecipientFalseOrderByCreatedAtDesc(String recipientEmail, String category, Boolean isRead);

    Optional<Notification> findByIdAndRecipientEmail(Long id, String recipientEmail);

    Optional<Notification> findFirstByRecipientEmailAndTitleAndRelatedIncidentIdAndRelatedIncidentTypeOrderByCreatedAtDesc(
        String recipientEmail, String title, Long relatedIncidentId, String relatedIncidentType);

    Optional<Notification> findFirstByRecipientEmailAndTitleAndMessageOrderByCreatedAtDesc(
        String recipientEmail, String title, String message);

    long countByRecipientEmailAndIsReadFalseAndIsDeletedByRecipientFalse(String recipientEmail);

    long countByRecipientEmailAndIsDeletedByRecipientFalse(String recipientEmail);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE LOWER(n.recipientEmail) = LOWER(:email) AND (n.isRead = false OR n.isRead IS NULL) AND n.isDeletedByRecipient = false")
    int markAllAsReadForRecipient(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isDeletedByRecipient = true WHERE n.id = :id AND LOWER(n.recipientEmail) = LOWER(:email)")
    int deleteNotificationForRecipient(@Param("id") Long id, @Param("email") String email);
}
