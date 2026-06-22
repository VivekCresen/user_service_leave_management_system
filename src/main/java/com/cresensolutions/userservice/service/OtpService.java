package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.model.UserAccount;

public interface OtpService {

    String createOtp(UserAccount user);

    void validateOtp(UserAccount user, String otp);

    void clearOtp(UserAccount user);

    void cleanupExpiredOtps();
}
