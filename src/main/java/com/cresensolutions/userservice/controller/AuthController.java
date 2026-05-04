package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.dto.LoginRequest;
import com.cresensolutions.userservice.dto.LoginResponse;
import com.cresensolutions.userservice.dto.OtpRequest;
import com.cresensolutions.userservice.dto.OtpResponse;
import com.cresensolutions.userservice.dto.OtpValidationRequest;
import com.cresensolutions.userservice.dto.ResetPasswordWithOtpRequest;
import com.cresensolutions.userservice.dto.RoleSummaryResponse;
import com.cresensolutions.userservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/forgot-password/request-otp")
    public OtpResponse requestPasswordResetOtp(@Valid @RequestBody OtpRequest request) {
        return authService.requestPasswordResetOtp(request);
    }

    @PostMapping("/forgot-password/verify-otp")
    public OtpResponse verifyPasswordResetOtp(@Valid @RequestBody OtpValidationRequest request) {
        return authService.verifyPasswordResetOtp(request);
    }

    @PostMapping("/forgot-password/reset")
    public LoginResponse resetPassword(@Valid @RequestBody ResetPasswordWithOtpRequest request) {
        return authService.resetPassword(request);
    }

    @GetMapping("/role-summary")
    public List<RoleSummaryResponse> roleSummary() {
        return authService.fetchRoleSummary();
    }
}
