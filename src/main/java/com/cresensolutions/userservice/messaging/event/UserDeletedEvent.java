package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

public record UserDeletedEvent(
        String email,
        String fullName,
        String username,
        String role,
        String deletedBy,
        String deletedByRole,
        Instant timestamp
) {
    public UserDeletedEvent(String email, String fullName, String username,
                            String role, String deletedBy, String deletedByRole) {
        this(email, fullName, username, role, deletedBy, deletedByRole, Instant.now());
    }
}
