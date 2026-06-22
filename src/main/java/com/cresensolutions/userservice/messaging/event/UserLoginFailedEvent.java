package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

public record UserLoginFailedEvent(String identifier, Instant timestamp) {
    public UserLoginFailedEvent(String identifier) {
        this(identifier, Instant.now());
    }
}
