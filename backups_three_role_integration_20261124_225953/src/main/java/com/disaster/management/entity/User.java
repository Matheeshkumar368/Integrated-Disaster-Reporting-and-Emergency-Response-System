package com.disaster.management.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_role", columnList = "role"),
    @Index(name = "idx_role_assignedZone", columnList = "role, assignedZone")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    private String name;
    private String phone;
    private String location;
    private String role;
    private String assignedZone; // Zone assigned to responder (e.g., Chennai, Coimbatore)
    
    private String groupClassification; // Leader, Helper, Rescuer
    private String additionalDetails; // Additional relevant details

    @Column(name = "availability_status", length = 30)
    private String availabilityStatus = "Available"; // Available, Assigned, On Duty, Unavailable

    // Emergency SOS Sound Shortcut Settings
    @Column(name = "sos_shortcut_enabled")
    private Boolean sosShortcutEnabled = true;

    @Column(name = "sos_shortcut_key", length = 50)
    private String sosShortcutKey = "Tab";

    @Column(name = "sos_sound_volume")
    private Integer sosSoundVolume = 80; // 0 to 100 percent

    // Admin Security PIN & Step-Up 2FA fields
    @JsonIgnore
    private String adminPin; // BCrypt-hashed numeric PIN

    private Boolean adminPinEnrolled = false;

    @JsonIgnore
    @Column(name = "admin_pin_recovery_code")
    private String adminPinRecoveryCode; // BCrypt-hashed emergency recovery code

    @Column(name = "backup_code_configured")
    private Boolean backupCodeConfigured = false;

    @JsonIgnore
    @Column(name = "backup_code_failed_attempts")
    private Integer backupCodeFailedAttempts = 0;

    @JsonIgnore
    @Column(name = "backup_code_locked_until")
    private LocalDateTime backupCodeLockedUntil;

    @JsonIgnore
    private Integer pinFailedAttempts = 0;

    @JsonIgnore
    private LocalDateTime pinLockedUntil;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "deactivation_reason")
    private String deactivationReason;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastLoginAt;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getAssignedZone() { return assignedZone; }
    public void setAssignedZone(String assignedZone) { this.assignedZone = assignedZone; }
    
    public String getGroupClassification() { return groupClassification; }
    public void setGroupClassification(String groupClassification) { this.groupClassification = groupClassification; }
    
    public String getAdditionalDetails() { return additionalDetails; }
    public void setAdditionalDetails(String additionalDetails) { this.additionalDetails = additionalDetails; }

    public String getAdminPin() { return adminPin; }
    public void setAdminPin(String adminPin) { this.adminPin = adminPin; }

    public Boolean getAdminPinEnrolled() { return adminPinEnrolled != null && adminPinEnrolled; }
    public void setAdminPinEnrolled(Boolean adminPinEnrolled) { this.adminPinEnrolled = adminPinEnrolled; }

    public String getAdminPinRecoveryCode() { return adminPinRecoveryCode; }
    public void setAdminPinRecoveryCode(String adminPinRecoveryCode) { this.adminPinRecoveryCode = adminPinRecoveryCode; }

    public Boolean getBackupCodeConfigured() { return backupCodeConfigured != null && backupCodeConfigured; }
    public void setBackupCodeConfigured(Boolean backupCodeConfigured) { this.backupCodeConfigured = backupCodeConfigured; }

    public Integer getBackupCodeFailedAttempts() { return backupCodeFailedAttempts != null ? backupCodeFailedAttempts : 0; }
    public void setBackupCodeFailedAttempts(Integer backupCodeFailedAttempts) { this.backupCodeFailedAttempts = backupCodeFailedAttempts; }

    public LocalDateTime getBackupCodeLockedUntil() { return backupCodeLockedUntil; }
    public void setBackupCodeLockedUntil(LocalDateTime backupCodeLockedUntil) { this.backupCodeLockedUntil = backupCodeLockedUntil; }

    public Integer getPinFailedAttempts() { return pinFailedAttempts != null ? pinFailedAttempts : 0; }
    public void setPinFailedAttempts(Integer pinFailedAttempts) { this.pinFailedAttempts = pinFailedAttempts; }

    public LocalDateTime getPinLockedUntil() { return pinLockedUntil; }
    public void setPinLockedUntil(LocalDateTime pinLockedUntil) { this.pinLockedUntil = pinLockedUntil; }

    public String getAvailabilityStatus() { return availabilityStatus != null ? availabilityStatus : "Available"; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public Boolean getSosShortcutEnabled() { return sosShortcutEnabled != null ? sosShortcutEnabled : true; }
    public void setSosShortcutEnabled(Boolean sosShortcutEnabled) { this.sosShortcutEnabled = sosShortcutEnabled; }

    public String getSosShortcutKey() { return (sosShortcutKey != null && !sosShortcutKey.isBlank()) ? sosShortcutKey : "Tab"; }
    public void setSosShortcutKey(String sosShortcutKey) { this.sosShortcutKey = sosShortcutKey; }

    public Integer getSosSoundVolume() { return (sosSoundVolume != null && sosSoundVolume >= 0 && sosSoundVolume <= 100) ? sosSoundVolume : 80; }
    public void setSosSoundVolume(Integer sosSoundVolume) { this.sosSoundVolume = sosSoundVolume; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public boolean isPinLocked() {
        return pinLockedUntil != null && pinLockedUntil.isAfter(LocalDateTime.now());
    }

    public void resetPinFailures() {
        this.pinFailedAttempts = 0;
        this.pinLockedUntil = null;
    }

    public void incrementPinFailures() {
        int current = getPinFailedAttempts() + 1;
        this.pinFailedAttempts = current;
        if (current >= 3) {
            this.pinLockedUntil = LocalDateTime.now().plusMinutes(15);
        }
    }

    public boolean isBackupCodeLocked() {
        return backupCodeLockedUntil != null && backupCodeLockedUntil.isAfter(LocalDateTime.now());
    }

    public void resetBackupCodeFailures() {
        this.backupCodeFailedAttempts = 0;
        this.backupCodeLockedUntil = null;
    }

    public void incrementBackupCodeFailures() {
        int current = getBackupCodeFailedAttempts() + 1;
        this.backupCodeFailedAttempts = current;
        if (current >= 5) {
            this.backupCodeLockedUntil = LocalDateTime.now().plusMinutes(15);
        }
    }

    public Boolean getActive() { return active != null ? active : true; }
    public void setActive(Boolean active) { this.active = active; }

    public String getDeactivationReason() { return deactivationReason; }
    public void setDeactivationReason(String deactivationReason) { this.deactivationReason = deactivationReason; }
}
