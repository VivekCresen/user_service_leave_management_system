package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

public record UserPasswordResetEvent(String email, Instant timestamp) {
    public UserPasswordResetEvent(String email) {
        this(email, Instant.now());
    }
}
