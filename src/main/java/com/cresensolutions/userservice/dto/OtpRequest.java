package com.cresensolutions.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email
) {

    public OtpRequest {
        email = email == null ? null : email.trim().toLowerCase();
    }
}
