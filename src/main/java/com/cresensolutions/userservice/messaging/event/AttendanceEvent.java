package com.cresensolutions.userservice.messaging.event;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceEvent(String username, Instant timestamp, LocalDate date, String type) {
    public AttendanceEvent(String username, LocalDate date, String type) {
        this(username, Instant.now(), date, type);
    }
}
