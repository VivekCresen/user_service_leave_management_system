package com.cresensolutions.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailServiceImpl(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    @Async("auditTaskExecutor")
    public void sendPasswordResetOtp(String email, String fullName, String otp) {
        if (mailProperties.fromAddress().isBlank()) {
            LOGGER.warn("Mail sender is not configured. OTP for {} is {}", email, otp);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.fromAddress());
        message.setTo(email);
        message.setSubject("Cresen Solutions Password Reset OTP");
        message.setText("""
                Hello %s,

                Your password reset OTP is: %s

                This OTP will expire in %d minutes.
                If you did not request this, please ignore this email.

                Cresen Solutions LLC
                """.formatted(fullName, otp, mailProperties.otpExpirationMinutes()));

        mailSender.send(message);
    }
}
