package com.cresensolutions.userservice.service;

public interface EmailService {

    void sendPasswordResetOtp(String email, String fullName, String otp);

    void sendNewUserCreatedEmail(
            String email,
            String fullName,
            Long userId,
            String companyId,
            String username,
            String role,
            String forgotPasswordLink
    );

    void sendUserDeletedEmail(
            String email,
            String fullName,
            String username,
            String role,
            String deletedByUsername,
            String deletedByRole
    );

    void sendUserRoleChangedEmail(
            String email,
            String fullName,
            String username,
            String previousRole,
            String newRole,
            String changedByUsername,
            String changedByRole,
            String loginUrl
    );
}
