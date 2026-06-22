package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.LoginRequest;
import com.cresensolutions.userservice.dto.LoginResponse;
import com.cresensolutions.userservice.dto.OtpRequest;
import com.cresensolutions.userservice.dto.OtpResponse;
import com.cresensolutions.userservice.dto.OtpValidationRequest;
import com.cresensolutions.userservice.dto.ResetPasswordWithOtpRequest;
import com.cresensolutions.userservice.dto.RoleSummaryResponse;

import java.util.List;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    OtpResponse requestPasswordResetOtp(OtpRequest request);

    OtpResponse verifyPasswordResetOtp(OtpValidationRequest request);

    LoginResponse resetPassword(ResetPasswordWithOtpRequest request);

    List<RoleSummaryResponse> fetchRoleSummary();
}
