package com.cresensolutions.userservice.service;

public interface EmailService {

    void sendPasswordResetOtp(String email, String fullName, String otp);
}
