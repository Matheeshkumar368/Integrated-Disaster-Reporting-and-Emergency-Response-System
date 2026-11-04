package com.disaster.management.dto;

public class PinRecoveryDto {

    private String backupCode;
    private String newPin;
    private String confirmPin;
    private String newBackupCode;
    private String confirmNewBackupCode;

    public PinRecoveryDto() {}

    public String getBackupCode() { return backupCode; }
    public void setBackupCode(String backupCode) { this.backupCode = backupCode; }

    public String getNewPin() { return newPin; }
    public void setNewPin(String newPin) { this.newPin = newPin; }

    public String getConfirmPin() { return confirmPin; }
    public void setConfirmPin(String confirmPin) { this.confirmPin = confirmPin; }

    public String getNewBackupCode() { return newBackupCode; }
    public void setNewBackupCode(String newBackupCode) { this.newBackupCode = newBackupCode; }

    public String getConfirmNewBackupCode() { return confirmNewBackupCode; }
    public void setConfirmNewBackupCode(String confirmNewBackupCode) { this.confirmNewBackupCode = confirmNewBackupCode; }
}
