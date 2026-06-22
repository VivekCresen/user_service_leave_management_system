package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;

public record UserLoginEvent(String username, Instant timestamp, String ip) {
    public UserLoginEvent(String username) {
        this(username, Instant.now(), "");
    }
}
