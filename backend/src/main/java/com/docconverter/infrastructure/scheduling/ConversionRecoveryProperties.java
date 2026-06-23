package com.docconverter.infrastructure.scheduling;

import static com.docconverter.infrastructure.config.ConfigurationValues.requirePositive;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "doc-converter.recovery")
public class ConversionRecoveryProperties {

    private boolean enabled = true;
    private Duration scanInterval = Duration.ofSeconds(30);
    private Duration initialDelay = Duration.ofSeconds(30);
    private Duration staleTimeout = Duration.ofMinutes(2);
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = requirePositive(scanInterval, "scanInterval");
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
        }
        this.initialDelay = initialDelay;
    }

    public Duration getStaleTimeout() {
        return staleTimeout;
    }

    public void setStaleTimeout(Duration staleTimeout) {
        this.staleTimeout = requirePositive(staleTimeout, "staleTimeout");
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

}
