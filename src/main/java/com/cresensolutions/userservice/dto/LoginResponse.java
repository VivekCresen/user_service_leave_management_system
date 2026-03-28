package com.cresensolutions.userservice.dto;

public record LoginResponse(
        String username,
        String email,
        String role,
        boolean active,
        String token,
        String message
) {
}
