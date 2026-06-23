package com.docconverter.application.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExponentialRetryBackoffPolicyTest {

    private final ExponentialRetryBackoffPolicy policy =
            new ExponentialRetryBackoffPolicy(
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(20),
                    2
            );

    @Test
    void increasesDelayExponentially() {
        assertThat(policy.delayAfterFailure(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayAfterFailure(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayAfterFailure(3)).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void capsDelayAtConfiguredMaximum() {
        assertThat(policy.delayAfterFailure(10)).isEqualTo(Duration.ofSeconds(20));
    }
}
