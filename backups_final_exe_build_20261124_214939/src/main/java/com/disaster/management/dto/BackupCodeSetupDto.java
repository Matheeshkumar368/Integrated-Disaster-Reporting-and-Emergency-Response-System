package com.disaster.management.dto;

public class BackupCodeSetupDto {

    private String email;
    private String backupCode;
    private String confirmBackupCode;

    public BackupCodeSetupDto() {}

    public BackupCodeSetupDto(String backupCode, String confirmBackupCode) {
        this.backupCode = backupCode;
        this.confirmBackupCode = confirmBackupCode;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBackupCode() { return backupCode; }
    public void setBackupCode(String backupCode) { this.backupCode = backupCode; }

    public String getConfirmBackupCode() { return confirmBackupCode; }
    public void setConfirmBackupCode(String confirmBackupCode) { this.confirmBackupCode = confirmBackupCode; }
}

