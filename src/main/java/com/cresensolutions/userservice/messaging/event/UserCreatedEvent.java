package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

public record UserCreatedEvent(
        String email,
        String fullName,
        Long userId,
        String companyId,
        String username,
        String role,
        String forgotPasswordLink,
        Instant timestamp
) {
    public UserCreatedEvent(String email, String fullName, Long userId,
                            String companyId, String username, String role,
                            String forgotPasswordLink) {
        this(email, fullName, userId, companyId, username, role, forgotPasswordLink, Instant.now());
    }
}
