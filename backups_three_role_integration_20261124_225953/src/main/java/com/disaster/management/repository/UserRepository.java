package com.disaster.management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.disaster.management.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    
    List<User> findByRoleAndAssignedZone(String role, String assignedZone);
    
    List<User> findByRole(String role);
    
    List<User> findByRoleAndAssignedZoneIsNullOrAssignedZoneEmpty(String role);

}
