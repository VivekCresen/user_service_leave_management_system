package com.cresensolutions.userservice.service.Impl;

import com.cresensolutions.userservice.service.AuthenticationAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthenticationAuditServiceImpl implements AuthenticationAuditService {

    @Override
    @Async("auditTaskExecutor")
    public void logLoginSuccess(String username) {
        log.info("Login succeeded for user={}", username);
    }

    @Override
    @Async("auditTaskExecutor")
    public void logLoginFailure(String usernameOrEmail) {
        log.warn("Login failed for identifier={}", usernameOrEmail);
    }

    @Override
    @Async("auditTaskExecutor")
    public void logPasswordReset(String email) {
        log.info("Password reset completed for email={}", email);
    }
}
