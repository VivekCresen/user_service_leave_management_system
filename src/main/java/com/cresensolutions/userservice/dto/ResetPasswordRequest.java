package com.cresensolutions.userservice.dto;

import com.cresensolutions.userservice.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,
        @NotBlank(message = "New password is required")
        @Size(
                min = ValidationPatterns.PASSWORD_MIN_LENGTH,
                max = ValidationPatterns.PASSWORD_MAX_LENGTH,
                message = "Password must be between 8 and 255 characters"
        )
        @Pattern(
                regexp = ValidationPatterns.STRICT_PASSWORD_REGEX,
                message = ValidationPatterns.STRICT_PASSWORD_MESSAGE
        )
        String newPassword
) {

    public ResetPasswordRequest {
        email = email == null ? null : email.trim().toLowerCase();
    }
}
