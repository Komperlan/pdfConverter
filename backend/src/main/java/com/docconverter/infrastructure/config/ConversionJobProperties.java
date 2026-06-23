package com.docconverter.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "doc-converter.job")
public class ConversionJobProperties {

    private Duration expiration = Duration.ofHours(24);
    private int maxAttempts = 3;

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("expiration must be positive");
        }
        this.expiration = expiration;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }
}
