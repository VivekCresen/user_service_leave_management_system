package com.cresensolutions.userservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
