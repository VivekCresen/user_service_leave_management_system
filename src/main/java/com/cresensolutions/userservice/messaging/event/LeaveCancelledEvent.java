package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

/** Mirror of the Leave Service LeaveCancelledEvent — consumed cross-service for SSE push. */
public record LeaveCancelledEvent(
        Long leaveId,
        Long userId,
        String username,
        String leaveType,
        String managerUsername,
        Instant timestamp
) {}
