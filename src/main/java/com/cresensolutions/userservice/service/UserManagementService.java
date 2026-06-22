package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.ManagedUserResponse;
import com.cresensolutions.userservice.dto.UpdateProfileRequest;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import com.cresensolutions.userservice.dto.UserDashboardResponse;

public interface UserManagementService {

    UserDashboardResponse getDashboard(String actorUsername);

    ManagedUserResponse createUser(CreateUserRequest request);

    ManagedUserResponse updateUser(Long userId, UpdateUserRequest request);

    ManagedUserResponse updateProfile(Long userId, UpdateProfileRequest request);

    void deleteUser(Long userId, String actorUsername);
}
