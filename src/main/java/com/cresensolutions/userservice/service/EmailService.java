package com.cresensolutions.userservice.service;

public interface EmailService {

    void sendPasswordResetOtp(String email, String fullName, String otp);

    void sendNewUserCreatedEmail(
            String email,
            String fullName,
            Long userId,
            String companyId,
            String username,
            String password,
            String role,
            String forgotPasswordLink
    );
}
