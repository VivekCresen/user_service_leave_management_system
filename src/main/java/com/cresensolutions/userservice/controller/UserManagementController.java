package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.ManagedUserResponse;
import com.cresensolutions.userservice.dto.UpdateProfileRequest;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import com.cresensolutions.userservice.dto.UserDashboardResponse;
import com.cresensolutions.userservice.service.UserManagementService;
import com.cresensolutions.userservice.sse.SseEmitterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final SseEmitterService sseEmitterService;

    public UserManagementController(UserManagementService userManagementService, SseEmitterService sseEmitterService) {
        this.userManagementService = userManagementService;
        this.sseEmitterService = sseEmitterService;
    }

    @GetMapping("/dashboard/{actorUsername}")
    public UserDashboardResponse dashboard(@PathVariable String actorUsername) {
        return userManagementService.getDashboard(actorUsername);
    }

    @PostMapping
    public ManagedUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        ManagedUserResponse result = userManagementService.createUser(request);
        sseEmitterService.broadcast("USERS_UPDATED", "created");
        return result;
    }

    @PutMapping("/{userId}")
    public ManagedUserResponse updateUser(@PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
        ManagedUserResponse result = userManagementService.updateUser(userId, request);
        sseEmitterService.broadcast("USERS_UPDATED", "updated");
        return result;
    }

    @PatchMapping("/{userId}/profile")
    public ManagedUserResponse updateProfile(@PathVariable Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        ManagedUserResponse result = userManagementService.updateProfile(userId, request);
        sseEmitterService.broadcast("USERS_UPDATED", "profile-updated");
        return result;
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(
            @PathVariable Long userId,
            @RequestParam @NotBlank(message = "Username is required") String actorUsername
    ) {
        userManagementService.deleteUser(userId, actorUsername);
        sseEmitterService.broadcast("USERS_UPDATED", "deleted");
    }
}
