package com.docconverter.application.support;

public final class TextValues {

    private TextValues() {
    }

    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    public static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
