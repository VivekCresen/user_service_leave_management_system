package com.cresensolutions.userservice.dto;

import java.util.Map;

public record ApiErrorResponse(String message, Map<String, String> errors) {

    public ApiErrorResponse(String message) {
        this(message, Map.of());
    }
}
