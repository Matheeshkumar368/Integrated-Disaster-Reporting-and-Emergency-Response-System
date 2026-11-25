package com.disaster.management.dto;

public class UserRegistrationDto {

    private String name;
    private String email;
    private String password;
    private String phone;
    private String location;
    private String role;
    private String assignedZone;
    private String groupClassification;
    private String additionalDetails;

    public UserRegistrationDto() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

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
}

