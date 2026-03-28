package com.cresensolutions.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username or email is required")
        @Size(max = 100, message = "Username or email must not exceed 100 characters")
        String username,
        @NotBlank(message = "Password is required")
        @Size(max = 255, message = "Password must not exceed 255 characters")
        String password
) {

    public LoginRequest {
        username = username == null ? null : username.trim();
    }
}
