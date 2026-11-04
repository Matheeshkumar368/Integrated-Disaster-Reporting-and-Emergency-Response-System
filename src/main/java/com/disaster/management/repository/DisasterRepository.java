package com.disaster.management.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.Disaster;

@Repository
public interface DisasterRepository extends JpaRepository<Disaster, Integer> {

    Optional<Disaster> findByTypeAndTimestamp(String type, LocalDateTime timestamp);

}