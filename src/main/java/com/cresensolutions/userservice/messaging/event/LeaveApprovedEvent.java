package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;
import java.util.List;

public record LeaveApprovedEvent(
        Long leaveId,
        Long userId,
        String username,
        String employeeEmail,
        String leaveType,
        Integer leaveTypeId,
        List<String> approvedDates,
        List<String> rejectedDates,
        String status,
        String actorUsername,
        String actorRole,
        String rejectionReason,
        double days,
        Instant timestamp
) {}
