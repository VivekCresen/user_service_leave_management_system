package com.cresensolutions.userservice.messaging;

import com.cresensolutions.userservice.config.RabbitMQConfig;
import com.cresensolutions.userservice.messaging.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class UserEventPublisher extends BaseEventPublisher {

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        super(rabbitTemplate);
    }

    public void publishLoginSuccess(String username) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_USER_LOGIN, new UserLoginEvent(username));
    }

    public void publishLoginFailed(String identifier) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_USER_LOGIN_FAILED, new UserLoginFailedEvent(identifier));
    }

    public void publishOtpRequested(String email, String fullName, String otp) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_USER_OTP_REQUESTED, new UserOtpRequestedEvent(email, fullName, otp));
    }

    public void publishPasswordReset(String email) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_USER_PASSWORD_RESET, new UserPasswordResetEvent(email));
    }

    public void publishUserCreated(String email, String fullName, Long userId,
                                   String companyId, String username, String role,
                                   String forgotPasswordLink) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_USER_CREATED,
                new UserCreatedEvent(email, fullName, userId, companyId, username, role, forgotPasswordLink));
    }

    public void publishUserDeleted(String email, String fullName, String username,
                                   String role, String deletedBy, String deletedByRole) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_USER_DELETED,
                new UserDeletedEvent(email, fullName, username, role, deletedBy, deletedByRole));
    }

    public void publishRoleChanged(String email, String fullName, String username,
                                   String previousRole, String newRole,
                                   String changedByUsername, String changedByRole,
                                   String loginUrl) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_USER_ROLE_CHANGED,
                new UserRoleChangedEvent(email, fullName, username, previousRole, newRole,
                        changedByUsername, changedByRole, loginUrl));
    }

    public void publishAttendanceCheckin(String username, java.time.LocalDate date) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_ATTENDANCE_CHECKIN, new AttendanceEvent(username, date, "CHECKIN"));
    }

    public void publishAttendanceCheckout(String username, java.time.LocalDate date) {
        publish(RabbitMQConfig.USER_EVENTS_EXCHANGE, RabbitMQConfig.RK_ATTENDANCE_CHECKOUT, new AttendanceEvent(username, date, "CHECKOUT"));
    }
}
