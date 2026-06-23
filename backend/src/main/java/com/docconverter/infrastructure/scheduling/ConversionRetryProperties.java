package com.docconverter.infrastructure.scheduling;

import static com.docconverter.infrastructure.config.ConfigurationValues.requirePositive;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "doc-converter.retry")
public class ConversionRetryProperties {

    private Duration initialDelay = Duration.ofSeconds(5);
    private Duration maxDelay = Duration.ofMinutes(1);
    private int multiplier = 2;

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = requirePositive(initialDelay, "initialDelay");
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public void setMaxDelay(Duration maxDelay) {
        this.maxDelay = requirePositive(maxDelay, "maxDelay");
    }

    public int getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(int multiplier) {
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be at least 1");
        }
        this.multiplier = multiplier;
    }

}
