package com.cresensolutions.userservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String USER_EVENTS_EXCHANGE    = "user.events";
    public static final String USER_DLX                = "user.dlx";

    public static final String RK_USER_LOGIN           = "user.login";
    public static final String RK_USER_LOGIN_FAILED    = "user.login.failed";
    public static final String RK_USER_OTP_REQUESTED   = "user.otp.requested";
    public static final String RK_USER_PASSWORD_RESET  = "user.password.reset";
    public static final String RK_USER_CREATED         = "user.created";
    public static final String RK_USER_DELETED         = "user.deleted";
    public static final String RK_USER_ROLE_CHANGED    = "user.role.changed";
    public static final String RK_ATTENDANCE_CHECKIN   = "attendance.checkin";
    public static final String RK_ATTENDANCE_CHECKOUT  = "attendance.checkout";

    public static final String Q_USER_LOGIN            = "q.user.login";
    public static final String Q_USER_LOGIN_FAILED     = "q.user.login.failed";
    public static final String Q_USER_OTP_REQUESTED    = "q.user.otp.requested";
    public static final String Q_USER_PASSWORD_RESET   = "q.user.password.reset";
    public static final String Q_USER_CREATED          = "q.user.created";
    public static final String Q_USER_DELETED          = "q.user.deleted";
    public static final String Q_USER_ROLE_CHANGED     = "q.user.role.changed";
    public static final String Q_ATTENDANCE_CHECKIN    = "q.attendance.checkin";
    public static final String Q_ATTENDANCE_CHECKOUT   = "q.attendance.checkout";

    public static final String Q_USER_CREATED_DLQ      = "q.user.created.dlq";
    public static final String Q_USER_OTP_DLQ          = "q.user.otp.requested.dlq";


    @Bean TopicExchange userEventsExchange() {
        return ExchangeBuilder.topicExchange(USER_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean DirectExchange userDlx() {
        return ExchangeBuilder.directExchange(USER_DLX).durable(true).build();
    }


    @Bean Queue userCreatedDlq() {
        return QueueBuilder.durable(Q_USER_CREATED_DLQ).build();
    }

    @Bean Queue userOtpDlq() {
        return QueueBuilder.durable(Q_USER_OTP_DLQ).build();
    }

    @Bean Binding userCreatedDlqBinding() {
        return BindingBuilder.bind(userCreatedDlq()).to(userDlx()).with(Q_USER_CREATED);
    }

    @Bean Binding userOtpDlqBinding() {
        return BindingBuilder.bind(userOtpDlq()).to(userDlx()).with(Q_USER_OTP_REQUESTED);
    }


    @Bean Queue qUserLogin() { return durable(Q_USER_LOGIN); }
    @Bean Queue qUserLoginFailed() { return durable(Q_USER_LOGIN_FAILED); }

    @Bean Queue qUserOtpRequested() {
        return QueueBuilder.durable(Q_USER_OTP_REQUESTED)
                .withArgument("x-dead-letter-exchange", USER_DLX)
                .withArgument("x-dead-letter-routing-key", Q_USER_OTP_REQUESTED)
                .build();
    }

    @Bean Queue qUserPasswordReset() { return durable(Q_USER_PASSWORD_RESET); }

    @Bean Queue qUserCreated() {
        return QueueBuilder.durable(Q_USER_CREATED)
                .withArgument("x-dead-letter-exchange", USER_DLX)
                .withArgument("x-dead-letter-routing-key", Q_USER_CREATED)
                .build();
    }

    @Bean Queue qUserDeleted() { return durable(Q_USER_DELETED); }
    @Bean Queue qUserRoleChanged() { return durable(Q_USER_ROLE_CHANGED); }
    @Bean Queue qAttendanceCheckin() { return durable(Q_ATTENDANCE_CHECKIN); }
    @Bean Queue qAttendanceCheckout() { return durable(Q_ATTENDANCE_CHECKOUT); }


    @Bean Binding bindUserLogin()         { return bind(qUserLogin(),         RK_USER_LOGIN); }
    @Bean Binding bindUserLoginFailed()   { return bind(qUserLoginFailed(),   RK_USER_LOGIN_FAILED); }
    @Bean Binding bindUserOtpRequested()  { return bind(qUserOtpRequested(),  RK_USER_OTP_REQUESTED); }
    @Bean Binding bindUserPasswordReset() { return bind(qUserPasswordReset(), RK_USER_PASSWORD_RESET); }
    @Bean Binding bindUserCreated()       { return bind(qUserCreated(),       RK_USER_CREATED); }
    @Bean Binding bindUserDeleted()       { return bind(qUserDeleted(),       RK_USER_DELETED); }
    @Bean Binding bindUserRoleChanged()   { return bind(qUserRoleChanged(),   RK_USER_ROLE_CHANGED); }
    @Bean Binding bindAttendanceCheckin() { return bind(qAttendanceCheckin(), RK_ATTENDANCE_CHECKIN); }
    @Bean Binding bindAttendanceCheckout(){ return bind(qAttendanceCheckout(),RK_ATTENDANCE_CHECKOUT); }


    public static final String LEAVE_EVENTS_EXCHANGE     = "leave.events";
    public static final String RK_LEAVE_APPROVED         = "leave.approved";
    public static final String RK_LEAVE_CANCELLED        = "leave.cancelled";
    public static final String RK_ATTENDANCE_CHECKIN_CS  = "attendance.checkin"; // published here, consumed by leave service

    public static final String Q_LEAVE_APPROVED_SSE      = "q.leave.approved.sse";   // User Service SSE push
    public static final String Q_LEAVE_CANCELLED_SSE     = "q.leave.cancelled.sse";  // User Service SSE push

    @Bean TopicExchange leaveEventsExchange() {
        return ExchangeBuilder.topicExchange(LEAVE_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean Queue qLeaveApprovedSse()   { return durable(Q_LEAVE_APPROVED_SSE); }
    @Bean Queue qLeaveCancelledSse()  { return durable(Q_LEAVE_CANCELLED_SSE); }

    @Bean Binding bindLeaveApprovedSse() {
        return BindingBuilder.bind(qLeaveApprovedSse()).to(leaveEventsExchange()).with(RK_LEAVE_APPROVED);
    }

    @Bean Binding bindLeaveCancelledSse() {
        return BindingBuilder.bind(qLeaveCancelledSse()).to(leaveEventsExchange()).with(RK_LEAVE_CANCELLED);
    }


    private Queue durable(String name) {
        return QueueBuilder.durable(name).build();
    }

    private Binding bind(Queue queue, String routingKey) {
        return BindingBuilder.bind(queue).to(userEventsExchange()).with(routingKey);
    }
}
