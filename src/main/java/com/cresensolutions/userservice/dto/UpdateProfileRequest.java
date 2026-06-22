package com.cresensolutions.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Actor username is required")
        @Size(max = 100)
        String actorUsername,

        @NotBlank(message = "Full name is required")
        @Size(max = 255)
        String fullName,

        @NotBlank(message = "Gender is required")
        @Size(max = 50)
        String gender
) {
    public UpdateProfileRequest {
        actorUsername = actorUsername == null ? null : actorUsername.trim();
        fullName      = fullName == null ? null : fullName.trim();
        gender        = gender == null ? null : gender.trim();
    }
}
