package com.cresensolutions.userservice.dto;

public class UserImportRow {
    private int rowNumber;
    private String fullName;
    private String username;
    private String email;
    private String password;
    private String role;
    private String managerUsername;
    private String gender;
    private String active;
    private String errorReason;

    public UserImportRow() {}

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getManagerUsername() { return managerUsername; }
    public void setManagerUsername(String managerUsername) { this.managerUsername = managerUsername; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getActive() { return active; }
    public void setActive(String active) { this.active = active; }

    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
}
