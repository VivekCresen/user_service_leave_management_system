package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

public record UserRoleChangedEvent(
        String email,
        String fullName,
        String username,
        String previousRole,
        String newRole,
        String changedByUsername,
        String changedByRole,
        String loginUrl,
        Instant timestamp
) {
    public UserRoleChangedEvent(String email, String fullName, String username,
                                String previousRole, String newRole,
                                String changedByUsername, String changedByRole,
                                String loginUrl) {
        this(email, fullName, username, previousRole, newRole,
                changedByUsername, changedByRole, loginUrl, Instant.now());
    }
}
