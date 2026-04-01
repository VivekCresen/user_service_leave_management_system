package com.cresensolutions.userservice.service;

public interface MailProperties {

    String fromAddress();

    long otpExpirationMinutes();

    String forgotPasswordUrl();

    String loginUrl();

    String logoPath();
}
