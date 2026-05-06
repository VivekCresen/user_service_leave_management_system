package com.cresensolutions.userservice.messaging;

import com.cresensolutions.userservice.config.RabbitMQConfig;
import com.cresensolutions.userservice.messaging.event.*;
import com.cresensolutions.userservice.service.EmailService;
import com.cresensolutions.userservice.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final EmailService emailService;
    private final SseEmitterService sseEmitterService;

    @RabbitListener(queues = RabbitMQConfig.Q_USER_LOGIN)
    public void onLoginSuccess(UserLoginEvent event) {
        log.info("[Audit] Login succeeded: user={} at={}", event.username(), event.timestamp());
        sseEmitterService.broadcast("USER_LOGIN", event.username());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_USER_LOGIN_FAILED)
    public void onLoginFailed(UserLoginFailedEvent event) {
        log.warn("[Audit] Login failed: identifier={} at={}", event.identifier(), event.timestamp());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_USER_OTP_REQUESTED)
    public void onOtpRequested(UserOtpRequestedEvent event) {
        log.info("[Email] Sending OTP to {}", event.email());
        emailService.sendPasswordResetOtp(event.email(), event.fullName(), event.otp());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_USER_PASSWORD_RESET)
    public void onPasswordReset(UserPasswordResetEvent event) {
        log.info("[Audit] Password reset completed: email={} at={}", event.email(), event.timestamp());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_USER_CREATED)
    public void onUserCreated(UserCreatedEvent event) {
        log.info("[Email] Sending welcome email to {} ({})", event.username(), event.email());
        emailService.sendNewUserCreatedEmail(
                event.email(), event.fullName(), event.userId(),
                event.companyId(), event.username(), event.role(),
                event.forgotPasswordLink());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_USER_DELETED)
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("[Email] Sending deletion email to {} ({})", event.username(), event.email());
        emailService.sendUserDeletedEmail(
                event.email(), event.fullName(), event.username(),
                event.role(), event.deletedBy(), event.deletedByRole());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_USER_ROLE_CHANGED)
    public void onRoleChanged(UserRoleChangedEvent event) {
        log.info("[Email] Sending role-change email to {} ({}→{})", event.username(), event.previousRole(), event.newRole());
        emailService.sendUserRoleChangedEmail(
                event.email(), event.fullName(), event.username(),
                event.previousRole(), event.newRole(),
                event.changedByUsername(), event.changedByRole(),
                event.loginUrl());
        sseEmitterService.sendToUser(event.username(), "ROLE_CHANGED", event.newRole());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_ATTENDANCE_CHECKIN)
    public void onCheckin(AttendanceEvent event) {
        log.info("[Attendance] Check-in: user={} date={}", event.username(), event.date());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_ATTENDANCE_CHECKOUT)
    public void onCheckout(AttendanceEvent event) {
        log.info("[Attendance] Check-out: user={} date={}", event.username(), event.date());
    }
}
