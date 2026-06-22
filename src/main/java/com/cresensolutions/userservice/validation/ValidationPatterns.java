package com.cresensolutions.userservice.validation;

public final class ValidationPatterns {

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 255;
    public static final String USERNAME_REGEX = "^[A-Za-z0-9._-]{3,100}$";
    public static final String USERNAME_MESSAGE =
            "Username must be 3 to 100 characters and contain only letters, numbers, dot, underscore, or hyphen";
    public static final String STRICT_PASSWORD_REGEX =
            "^(?=\\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).*$";
    public static final String STRICT_PASSWORD_MESSAGE =
            "Password must include uppercase, lowercase, number, and special character with no spaces";

    private ValidationPatterns() {
    }
}
