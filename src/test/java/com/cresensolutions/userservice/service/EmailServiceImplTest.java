package com.cresensolutions.userservice.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailProperties mailProperties;

    @Test
    void shouldSwallowMailSendErrorsWhenSendingNewUserEmail() {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        EmailServiceImpl emailService = new EmailServiceImpl(mailSender, mailProperties);

        when(mailProperties.fromAddress()).thenReturn("viveksinhchavda@gmail.com");
        when(mailProperties.logoPath()).thenReturn("");
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("SMTP rejected message")).when(mailSender).send(message);

        assertDoesNotThrow(() -> emailService.sendNewUserCreatedEmail(
                "viveksinhchavda@gmail.com",
                "New Employee",
                42L,
                "CRESEN042",
                "new.employee",
                "EMPLOYEE",
                "http://localhost:4200/forgot-password"
        ));

        verify(mailSender).send(message);
    }
}
