package com.cresensolutions.userservice.common;

import java.util.Locale;

public final class StringUtils {

    private StringUtils() {}

    /** Returns empty string if null, otherwise the value as-is. */
    public static String safe(String value) {
        return value != null ? value : "";
    }

    /** Trims and lowercases; returns empty string for null. */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /** Trims and uppercases; returns empty string for null. Used for role normalization. */
    public static String normalizeRole(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** Trims to null — returns null if value is null or blank after trim. */
    public static String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims the value; returns null if null input. */
    public static String normalizeOptional(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Trims the value and throws {@link IllegalArgumentException} if blank.
     *
     * @param value   the string to validate
     * @param message exception message when blank
     * @return trimmed non-blank value
     */
    public static String requireNonBlank(String value, String message) {
        String trimmed = normalizeOptional(value);
        if (trimmed == null || trimmed.isBlank()) throw new IllegalArgumentException(message);
        return trimmed;
    }
}
