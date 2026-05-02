package com.cresensolutions.userservice.messaging;

import com.cresensolutions.userservice.config.RabbitMQConfig;
import com.cresensolutions.userservice.messaging.event.LeaveApprovedEvent;
import com.cresensolutions.userservice.messaging.event.LeaveCancelledEvent;
import com.cresensolutions.userservice.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes cross-service events published by Leave Service.
 *
 * #18 — leave.approved → push real-time SSE notification to the employee's browser.
 *       The employee sees "Your leave was approved" without polling.
 *
 * #17 — leave.cancelled → push SSE to manager so their dashboard updates in real time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveEventCrossServiceListener {

    private final SseEmitterService sseEmitterService;

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_APPROVED_SSE)
    public void onLeaveApproved(LeaveApprovedEvent event) {
        log.info("[CrossService] Leave approved: leaveId={} user={}", event.leaveId(), event.username());

        if (event.username() != null && !event.username().isBlank()) {
            // Push to the employee's open SSE connection
            String payload = String.format(
                    "{\"leaveId\":%d,\"leaveType\":\"%s\",\"status\":\"APPROVED\",\"approvedBy\":\"%s\"}",
                    event.leaveId(),
                    event.leaveType() != null ? event.leaveType() : "",
                    event.actorUsername() != null ? event.actorUsername() : "");
            sseEmitterService.sendToUser(event.username(), "LEAVE_APPROVED", payload);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_CANCELLED_SSE)
    public void onLeaveCancelled(LeaveCancelledEvent event) {
        log.info("[CrossService] Leave cancelled: leaveId={} user={}", event.leaveId(), event.username());

        // Push to the employee so their UI reflects the cancellation
        if (event.username() != null && !event.username().isBlank()) {
            String payload = String.format(
                    "{\"leaveId\":%d,\"leaveType\":\"%s\",\"status\":\"CANCELLED\"}",
                    event.leaveId(),
                    event.leaveType() != null ? event.leaveType() : "");
            sseEmitterService.sendToUser(event.username(), "LEAVE_CANCELLED", payload);
        }

        // Also push to the manager so their pending-approvals list updates
        if (event.managerUsername() != null && !event.managerUsername().isBlank()) {
            String payload = String.format(
                    "{\"leaveId\":%d,\"cancelledBy\":\"%s\"}",
                    event.leaveId(), event.username() != null ? event.username() : "");
            sseEmitterService.sendToUser(event.managerUsername(), "LEAVE_CANCELLED", payload);
        }
    }
}
