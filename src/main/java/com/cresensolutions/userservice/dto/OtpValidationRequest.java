package com.cresensolutions.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpValidationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,
        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits")
        String otp
) {
    public OtpValidationRequest {
        email = email == null ? null : email.trim().toLowerCase();
        otp = otp == null ? null : otp.trim();
    }
}
