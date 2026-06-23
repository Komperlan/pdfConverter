package com.docconverter.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversionAttemptTest {

    @Test
    void failedAttemptKeepsExternalRequestId() {
        Instant startedAt = Instant.parse("2026-06-22T09:00:00Z");
        ConversionAttempt attempt = ConversionAttempt.start(1, startedAt);

        attempt.markFailed(
                ConversionErrorCode.CONVERTER_TIMEOUT,
                "Document conversion timed out",
                "carbone-request-42",
                startedAt.plusSeconds(10)
        );

        assertThat(attempt.getStatus()).isEqualTo(ConversionAttemptStatus.FAILED);
        assertThat(attempt.getExternalRequestId()).isEqualTo("carbone-request-42");
        assertThat(attempt.getDurationMs()).isEqualTo(10_000);
    }

    @Test
    void invalidRequestIdDoesNotPartiallyFinishAttempt() {
        Instant startedAt = Instant.parse("2026-06-22T09:00:00Z");
        ConversionAttempt attempt = ConversionAttempt.start(1, startedAt);

        assertThatThrownBy(() -> attempt.markFailed(
                ConversionErrorCode.CONVERTER_TIMEOUT,
                "Document conversion timed out",
                "x".repeat(129),
                startedAt.plusSeconds(10)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(attempt.getStatus()).isEqualTo(ConversionAttemptStatus.STARTED);
        assertThat(attempt.getFinishedAt()).isNull();
        assertThat(attempt.getDurationMs()).isNull();
    }
}
