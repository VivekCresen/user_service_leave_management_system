package com.cresensolutions.userservice.dto;

import com.cresensolutions.userservice.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 100, message = "Username must not exceed 100 characters")
        String actorUsername,

        String companyId,

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must not exceed 255 characters")
        String fullName,

        @NotBlank(message = "Username is required")
        @Pattern(regexp = ValidationPatterns.USERNAME_REGEX, message = ValidationPatterns.USERNAME_MESSAGE)
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 200, message = "Email must not exceed 200 characters")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        @NotBlank(message = "Role is required")
        String role,

        @NotNull(message = "Active status is required")
        Boolean active,

        @NotBlank(message = "Gender is required")
        @Size(max = 50, message = "Gender must not exceed 50 characters")
        String gender
) {

    public CreateUserRequest {
        actorUsername = actorUsername == null ? null : actorUsername.trim();
        companyId = companyId == null ? null : companyId.trim();
        fullName = fullName == null ? null : fullName.trim();
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim().toLowerCase();
        role = role == null ? null : role.trim().toUpperCase();
        gender = gender == null ? null : gender.trim();
    }
}
