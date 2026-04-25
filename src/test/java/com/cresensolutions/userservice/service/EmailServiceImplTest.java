package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.repository.EmailTemplateRepository;
import com.cresensolutions.userservice.service.Impl.EmailServiceImpl;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.File;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    private EmailServiceImpl emailService;

    private MailProperties configuredMail;
    private MailProperties unconfiguredMail;

    @BeforeEach
    void setUp() {
        configuredMail = new StubMailProperties("noreply@cresensolutions.com", 10, "", "", "");
        unconfiguredMail = new StubMailProperties("", 10, "", "", "");

        lenient().when(emailTemplateRepository.findByTemplateTypeAndActiveTrue(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void sendPasswordResetOtp_configuredSender_sendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, configuredMail, emailTemplateRepository);

        emailService.sendPasswordResetOtp("user@cresensolutions.com", "Alice", "123456");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetOtp_unconfiguredSender_skipsEmail() {
        emailService = new EmailServiceImpl(mailSender, unconfiguredMail, emailTemplateRepository);

        emailService.sendPasswordResetOtp("user@cresensolutions.com", "Alice", "123456");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendNewUserCreatedEmail_configuredSender_sendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, configuredMail, emailTemplateRepository);

        emailService.sendNewUserCreatedEmail(
                "user@cresensolutions.com", "Bob", 42L, "CRESEN004",
                "bob", "EMPLOYEE", "http://localhost:4200/forgot-password");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendNewUserCreatedEmail_nullUserId_sendsEmailWithPendingId() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, configuredMail, emailTemplateRepository);

        emailService.sendNewUserCreatedEmail(
                "user@cresensolutions.com", "Bob", null, "CRESEN004",
                "bob", "EMPLOYEE", null);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendNewUserCreatedEmail_unconfiguredSender_skipsEmail() {
        emailService = new EmailServiceImpl(mailSender, unconfiguredMail, emailTemplateRepository);

        emailService.sendNewUserCreatedEmail(
                "user@cresensolutions.com", "Bob", 1L, "CRESEN004",
                "bob", "EMPLOYEE", null);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendUserDeletedEmail_configuredSender_sendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, configuredMail, emailTemplateRepository);

        emailService.sendUserDeletedEmail(
                "user@cresensolutions.com", "Carol", "carol", "EMPLOYEE",
                "admin", "ADMIN");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendUserDeletedEmail_unconfiguredSender_skipsEmail() {
        emailService = new EmailServiceImpl(mailSender, unconfiguredMail, emailTemplateRepository);

        emailService.sendUserDeletedEmail(
                "user@cresensolutions.com", "Carol", "carol", "EMPLOYEE",
                "admin", "ADMIN");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendUserRoleChangedEmail_configuredSender_sendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, configuredMail, emailTemplateRepository);

        emailService.sendUserRoleChangedEmail(
                "user@cresensolutions.com", "Dave", "dave",
                "EMPLOYEE", "MANAGER",
                "admin", "ADMIN",
                "http://localhost:4200/login");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendUserRoleChangedEmail_nullLoginUrl_fallsBackToMailProperties() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, configuredMail, emailTemplateRepository);

        emailService.sendUserRoleChangedEmail(
                "user@cresensolutions.com", "Dave", "dave",
                "EMPLOYEE", "MANAGER",
                "admin", "ADMIN",
                null);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendUserRoleChangedEmail_unconfiguredSender_skipsEmail() {
        emailService = new EmailServiceImpl(mailSender, unconfiguredMail, emailTemplateRepository);

        emailService.sendUserRoleChangedEmail(
                "user@cresensolutions.com", "Dave", "dave",
                "EMPLOYEE", "MANAGER",
                "admin", "ADMIN", null);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordResetOtp_withNonExistentLogoPath_sendsEmailWithoutLogo() {
        MailProperties mailWithBadLogo = new StubMailProperties(
                "noreply@cresensolutions.com", 10, "", "", "/nonexistent/logo.png");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, mailWithBadLogo, emailTemplateRepository);

        emailService.sendPasswordResetOtp("user@cresensolutions.com", "Alice", "123456");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendNewUserCreatedEmail_blankForgotPasswordLink_usesMailPropertiesUrl() {
        MailProperties mailWithUrl = new StubMailProperties(
                "noreply@cresensolutions.com", 10, "http://reset.example.com", "", "");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, mailWithUrl, emailTemplateRepository);

        emailService.sendNewUserCreatedEmail(
                "user@cresensolutions.com", "Bob", 1L, "CRESEN004",
                "bob", "EMPLOYEE", "  ");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendUserRoleChangedEmail_blankLoginUrl_usesMailPropertiesUrl() {
        MailProperties mailWithUrl = new StubMailProperties(
                "noreply@cresensolutions.com", 10, "", "http://login.example.com", "");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, mailWithUrl, emailTemplateRepository);

        emailService.sendUserRoleChangedEmail(
                "user@cresensolutions.com", "Dave", "dave",
                "EMPLOYEE", "MANAGER", "admin", "ADMIN", "  ");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetOtp_unconfiguredSender_skipsAndLogsWarn() {
        emailService = new EmailServiceImpl(mailSender, unconfiguredMail, emailTemplateRepository);

        emailService.sendPasswordResetOtp("user@cresensolutions.com", "Alice", "123456");

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendUserDeletedEmail_unconfiguredSender_skipsAndLogsWarn() {
        emailService = new EmailServiceImpl(mailSender, unconfiguredMail, emailTemplateRepository);

        emailService.sendUserDeletedEmail(
                "user@cresensolutions.com", "Carol", "carol", "EMPLOYEE", "admin", "ADMIN");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendUserRoleChangedEmail_unconfiguredSender_skipsAndLogsWarn() {
        emailService = new EmailServiceImpl(mailSender, unconfiguredMail, emailTemplateRepository);

        emailService.sendUserRoleChangedEmail(
                "user@cresensolutions.com", "Dave", "dave",
                "EMPLOYEE", "MANAGER", "admin", "ADMIN", null);

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendPasswordResetOtp_withExistingLogoFile_includesLogoInEmail() throws Exception {
       File tempLogo = File.createTempFile("logo", ".png");
        tempLogo.deleteOnExit();

        MailProperties mailWithRealLogo = new StubMailProperties(
                "noreply@cresensolutions.com", 10, "", "", tempLogo.getAbsolutePath());
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, mailWithRealLogo, emailTemplateRepository);

        emailService.sendPasswordResetOtp("user@cresensolutions.com", "Alice", "123456");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetOtp_nullLogoPath_sendsEmailWithoutLogo() {
        MailProperties mailWithNullLogo = new StubMailProperties(
                "noreply@cresensolutions.com", 10, "", "", null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, mailWithNullLogo, emailTemplateRepository);

        emailService.sendPasswordResetOtp("user@cresensolutions.com", "Alice", "123456");

        verify(mailSender).send(mimeMessage);
    }

    private record StubMailProperties(
            String fromAddress,
            long otpExpirationMinutes,
            String forgotPasswordUrl,
            String loginUrl,
            String logoPath
    ) implements MailProperties {
    }
}
