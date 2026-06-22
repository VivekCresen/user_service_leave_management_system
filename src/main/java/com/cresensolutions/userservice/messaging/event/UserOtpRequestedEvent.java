package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

public record UserOtpRequestedEvent(String email, String fullName, String otp, Instant timestamp) {
    public UserOtpRequestedEvent(String email, String fullName, String otp) {
        this(email, fullName, otp, Instant.now());
    }
}
