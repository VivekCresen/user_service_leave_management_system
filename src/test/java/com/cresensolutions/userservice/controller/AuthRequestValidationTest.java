package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.LoginRequest;
import com.cresensolutions.userservice.dto.ResetPasswordWithOtpRequest;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validator = null;
    }

    @Test
    void shouldRejectBlankLoginFields() {
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(new LoginRequest("   ", ""));

        assertEquals(2, violations.size());
        assertTrue(containsViolation(violations, "username", "Username or email is required"));
        assertTrue(containsViolation(violations, "password", "Password is required"));
    }

    @Test
    void shouldRejectInvalidResetPayload() {
        Set<ConstraintViolation<ResetPasswordWithOtpRequest>> violations = validator.validate(
                new ResetPasswordWithOtpRequest("bad-email", "123", "weakpass1")
        );

        assertEquals(3, violations.size());
        assertTrue(containsViolation(violations, "email", "Please provide a valid email address"));
        assertTrue(containsViolation(violations, "otp", "OTP must be 6 digits"));
        assertTrue(containsViolation(
                violations,
                "newPassword",
                "Password must include uppercase, lowercase, number, and special character with no spaces"
        ));
    }

    @Test
    void shouldAllowCreateUserPayloadWithoutCompanyId() {
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(new CreateUserRequest(
                "admin",
                null,
                "New Employee",
                "new.employee",
                "new.employee@cresen.com",
                "TempPass@123",
                "EMPLOYEE",
                true,
                "Female"
        ));

        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldAllowUpdateUserPayloadWithoutCompanyId() {
        Set<ConstraintViolation<UpdateUserRequest>> violations = validator.validate(new UpdateUserRequest(
                "admin",
                null,
                "Updated Employee",
                "updated.employee",
                "updated.employee@cresen.com",
                "",
                "EMPLOYEE",
                true,
                "Female"
        ));

        assertTrue(violations.isEmpty());
    }

    private static boolean containsViolation(
            Set<? extends ConstraintViolation<?>> violations,
            String field,
            String message
    ) {
        return violations.stream().anyMatch(violation ->
                field.equals(violation.getPropertyPath().toString()) && message.equals(violation.getMessage())
        );
    }
}
