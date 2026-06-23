package com.docconverter.application.processing;

import java.time.Duration;
import java.util.Objects;

public final class ExponentialRetryBackoffPolicy implements RetryBackoffPolicy {

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final int multiplier;

    public ExponentialRetryBackoffPolicy(
            Duration initialDelay,
            Duration maxDelay,
            int multiplier
    ) {
        this.initialDelay = requirePositive(initialDelay, "initialDelay");
        this.maxDelay = requirePositive(maxDelay, "maxDelay");
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must not be shorter than initialDelay");
        }
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be at least 1");
        }
        this.multiplier = multiplier;
    }

    @Override
    public Duration delayAfterFailure(int failedAttemptNumber) {
        if (failedAttemptNumber <= 0) {
            throw new IllegalArgumentException("failedAttemptNumber must be positive");
        }

        Duration delay = initialDelay;
        for (int attempt = 1; attempt < failedAttemptNumber; attempt++) {
            if (delay.compareTo(maxDelay) >= 0) {
                return maxDelay;
            }
            try {
                delay = delay.multipliedBy(multiplier);
            } catch (ArithmeticException overflow) {
                return maxDelay;
            }
        }
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
