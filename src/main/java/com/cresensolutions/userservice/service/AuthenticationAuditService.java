package com.cresensolutions.userservice.service;

public interface AuthenticationAuditService {

    void logLoginSuccess(String username);

    void logLoginFailure(String usernameOrEmail);

    void logPasswordReset(String email);
}
