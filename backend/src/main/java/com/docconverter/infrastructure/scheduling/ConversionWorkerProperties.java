package com.docconverter.infrastructure.scheduling;

import static com.docconverter.infrastructure.config.ConfigurationValues.requirePositive;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "doc-converter.worker")
public class ConversionWorkerProperties {

    private boolean enabled = true;
    private int maxParallelism = 2;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration initialDelay = Duration.ofSeconds(1);
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxParallelism() {
        return maxParallelism;
    }

    public void setMaxParallelism(int maxParallelism) {
        if (maxParallelism <= 0) {
            throw new IllegalArgumentException("maxParallelism must be positive");
        }
        this.maxParallelism = maxParallelism;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = requirePositive(pollInterval, "pollInterval");
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

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = requirePositive(shutdownTimeout, "shutdownTimeout");
    }

}
