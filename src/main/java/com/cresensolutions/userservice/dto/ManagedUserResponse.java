package com.cresensolutions.userservice.dto;

import java.time.Instant;

public record ManagedUserResponse(
        Long id,
        String companyId,
        String username,
        String fullName,
        String email,
        String role,
        boolean active,
        String gender,
        String createdBy,
        String updatedBy,
        Instant createDate,
        Instant updateDate,
        Instant lastLogin,
        boolean canEdit,
        boolean canDelete,
        Long countryId,
        String countryName,
        String countryCode,
        String countryFlagEmoji,
        Long phoneCodeId,
        String dialCode,
        String phoneNumber
) {
}
