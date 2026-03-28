package com.cresensolutions.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationAuditServiceImpl implements AuthenticationAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationAuditServiceImpl.class);

    @Override
    @Async("auditTaskExecutor")
    public void logLoginSuccess(String username) {
        LOGGER.info("Login succeeded for user={}", username);
    }

    @Override
    @Async("auditTaskExecutor")
    public void logLoginFailure(String usernameOrEmail) {
        LOGGER.warn("Login failed for identifier={}", usernameOrEmail);
    }

    @Override
    @Async("auditTaskExecutor")
    public void logPasswordReset(String email) {
        LOGGER.info("Password reset completed for email={}", email);
    }
}
