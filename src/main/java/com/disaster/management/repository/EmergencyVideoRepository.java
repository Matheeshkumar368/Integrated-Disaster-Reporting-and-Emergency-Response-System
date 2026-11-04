package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.EmergencyVideo;

@Repository
public interface EmergencyVideoRepository extends JpaRepository<EmergencyVideo, Long> {

    List<EmergencyVideo> findByIsActiveTrueOrderByCreatedAtDesc();

    List<EmergencyVideo> findByCategoryIgnoreCaseAndIsActiveTrue(String category);

    List<EmergencyVideo> findByCategoryContainingIgnoreCaseAndIsActiveTrue(String category);

    List<EmergencyVideo> findByLanguageIgnoreCaseAndIsActiveTrue(String language);

    List<EmergencyVideo> findByCategoryIgnoreCaseAndLanguageIgnoreCaseAndIsActiveTrue(String category, String language);

    @Query("SELECT v FROM EmergencyVideo v WHERE v.isActive = true " +
           "AND (:category IS NULL OR :category = '' OR LOWER(v.category) LIKE LOWER(CONCAT('%', :category, '%'))) " +
           "AND (:language IS NULL OR :language = '' OR LOWER(v.language) = LOWER(:language)) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(v.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(v.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(v.sourceOrganization) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY v.id ASC")
    List<EmergencyVideo> searchVideos(
        @Param("search") String search,
        @Param("category") String category,
        @Param("language") String language
    );

    long countByIsActiveTrue();
}
