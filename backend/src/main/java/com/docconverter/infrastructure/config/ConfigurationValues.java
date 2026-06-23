package com.docconverter.infrastructure.config;

import java.time.Duration;

public final class ConfigurationValues {

    private ConfigurationValues() {
    }

    public static Duration requirePositive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
