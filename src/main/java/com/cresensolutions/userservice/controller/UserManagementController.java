package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.ManagedUserResponse;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import com.cresensolutions.userservice.dto.UserDashboardResponse;
import com.cresensolutions.userservice.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping("/dashboard/{actorUsername}")
    public UserDashboardResponse dashboard(@PathVariable String actorUsername) {
        return userManagementService.getDashboard(actorUsername);
    }

    @PostMapping
    public ManagedUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }

    @PutMapping("/{userId}")
    public ManagedUserResponse updateUser(@PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
        return userManagementService.updateUser(userId, request);
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(
            @PathVariable Long userId,
            @RequestParam @NotBlank(message = "Actor username is required") String actorUsername
    ) {
        userManagementService.deleteUser(userId, actorUsername);
    }
}
