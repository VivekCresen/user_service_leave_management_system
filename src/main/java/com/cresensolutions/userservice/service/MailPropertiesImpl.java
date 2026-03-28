package com.cresensolutions.userservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailPropertiesImpl implements MailProperties {

    private final String fromAddress;
    private final long otpExpirationMinutes;

    public MailPropertiesImpl(
            @Value("${app.mail.from:}") String fromAddress,
            @Value("${security.otp.expiration-minutes}") long otpExpirationMinutes
    ) {
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
        this.otpExpirationMinutes = otpExpirationMinutes;
    }

    @Override
    public String fromAddress() {
        return fromAddress;
    }

    @Override
    public long otpExpirationMinutes() {
        return otpExpirationMinutes;
    }
}
