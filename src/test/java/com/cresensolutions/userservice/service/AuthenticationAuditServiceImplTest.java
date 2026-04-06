package com.cresensolutions.userservice.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AuthenticationAuditServiceImplTest {

    private final AuthenticationAuditServiceImpl authenticationAuditService = new AuthenticationAuditServiceImpl();

    @Test
    void testLogLoginSuccess() {
        assertDoesNotThrow(() -> authenticationAuditService.logLoginSuccess("testuser"));
    }

    @Test
    void testLogLoginFailure() {
        assertDoesNotThrow(() -> authenticationAuditService.logLoginFailure("testuser@example.com"));
    }

    @Test
    void testLogPasswordReset() {
        assertDoesNotThrow(() -> authenticationAuditService.logPasswordReset("testuser@example.com"));
    }
}
