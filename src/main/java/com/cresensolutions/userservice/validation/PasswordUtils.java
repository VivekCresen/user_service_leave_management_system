package com.cresensolutions.userservice.validation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PasswordUtils {

    private PasswordUtils() {}

    public static String decodeBase64(String encodedPassword) {
        try {
            return new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Password must be valid Base64.");
        }
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < ValidationPatterns.PASSWORD_MIN_LENGTH
                || password.length() > ValidationPatterns.PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("Password must be between 8 and 255 characters");
        }
        if (!password.matches(ValidationPatterns.STRICT_PASSWORD_REGEX)) {
            throw new IllegalArgumentException(ValidationPatterns.STRICT_PASSWORD_MESSAGE);
        }
    }
}
