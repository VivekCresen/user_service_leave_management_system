package com.cresensolutions.userservice.dto;

import java.util.List;

public record UserDashboardResponse(
        ManagedUserResponse actor,
        List<ManagedUserResponse> users,
        List<String> assignableRoles,
        boolean canManageUsers,
        long totalUsers,
        long activeUsers,
        long inactiveUsers,
        long adminCount,
        long managerCount,
        long employeeCount
) {
}
