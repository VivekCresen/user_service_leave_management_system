package com.cresensolutions.userservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailPropertiesImpl implements MailProperties {

    private final String fromAddress;
    private final long otpExpirationMinutes;
    private final String forgotPasswordUrl;
    private final String logoPath;

    public MailPropertiesImpl(
            @Value("${app.mail.from:}") String fromAddress,
            @Value("${security.otp.expiration-minutes}") long otpExpirationMinutes,
            @Value("${app.frontend.forgot-password-url:http://localhost:4200/forgot-password}") String forgotPasswordUrl,
            @Value("${app.mail.logo-path:/home/vivek/Documents/CresenProject/front-end/frontend_leave_management_system/public/assets/logo-6.png}") String logoPath
    ) {
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.forgotPasswordUrl = forgotPasswordUrl == null ? "" : forgotPasswordUrl.trim();
        this.logoPath = logoPath == null ? "" : logoPath.trim();
    }

    @Override
    public String fromAddress() {
        return fromAddress;
    }

    @Override
    public long otpExpirationMinutes() {
        return otpExpirationMinutes;
    }

    @Override
    public String forgotPasswordUrl() {
        return forgotPasswordUrl;
    }

    @Override
    public String logoPath() {
        return logoPath;
    }
}
