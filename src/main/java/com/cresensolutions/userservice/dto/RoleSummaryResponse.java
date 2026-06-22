package com.cresensolutions.userservice.dto;

import java.util.List;

public record RoleSummaryResponse(
        Long roleId,
        String roleName,
        long userCount,
        List<String> usernames
) {
}
