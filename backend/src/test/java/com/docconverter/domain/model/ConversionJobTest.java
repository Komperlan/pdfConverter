package com.docconverter.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversionJobTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void followsSuccessfulLifecycle() {
        ConversionJob job = newJob(2);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        Instant finishedAt = startedAt.plusSeconds(10);

        job.startProcessing(startedAt);
        ConversionAttempt attempt = ConversionAttempt.start(1, startedAt);
        job.registerAttempt(attempt);
        attempt.markSucceeded("request-1", finishedAt);
        job.complete("result/report.pdf", finishedAt);

        assertThat(job.getStatus()).isEqualTo(ConversionStatus.COMPLETED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getResultFilePath()).isEqualTo("result/report.pdf");
        assertThat(job.getUpdatedAt()).isEqualTo(finishedAt);
        assertThat(attempt.getExternalRequestId()).isEqualTo("request-1");
    }

    @Test
    void schedulesRetryAndEnforcesMaximumAttempts() {
        ConversionJob job = newJob(2);
        Instant firstStartedAt = CREATED_AT.plusSeconds(1);
        Instant firstFinishedAt = firstStartedAt.plusSeconds(10);
        Instant retryAt = firstFinishedAt.plusSeconds(5);

        job.startProcessing(firstStartedAt);
        job.registerAttempt(ConversionAttempt.start(1, firstStartedAt));
        job.scheduleRetry(
                ConversionErrorCode.CONVERTER_TIMEOUT,
                "Document conversion timed out",
                firstFinishedAt,
                retryAt
        );
        job.startProcessing(retryAt);
        job.registerAttempt(ConversionAttempt.start(2, retryAt));

        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThatThrownBy(() -> job.registerAttempt(
                ConversionAttempt.start(3, retryAt.plusSeconds(1))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Maximum conversion attempts reached");
    }

    @Test
    void expiresCompletedJobAndMarksCleanup() {
        ConversionJob job = newJob(1);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        Instant finishedAt = startedAt.plusSeconds(5);
        Instant expiredAt = job.getExpiresAt();

        job.startProcessing(startedAt);
        job.registerAttempt(ConversionAttempt.start(1, startedAt));
        job.complete("result/report.pdf", finishedAt);
        job.expire(expiredAt);
        job.markCleanupCompleted(expiredAt.plusSeconds(1));

        assertThat(job.getStatus()).isEqualTo(ConversionStatus.EXPIRED);
        assertThat(job.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(job.getCleanupCompletedAt()).isEqualTo(expiredAt.plusSeconds(1));
    }

    @Test
    void rejectsInvalidStateTransition() {
        ConversionJob job = newJob(1);

        assertThatThrownBy(() -> job.complete("result/report.pdf", CREATED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status CREATED");
    }

    private ConversionJob newJob(int maxAttempts) {
        return ConversionJob.create(
                "report.docx",
                "report.docx",
                "source/report.docx",
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/x-tika-ooxml",
                128,
                "a".repeat(64),
                CREATED_AT,
                CREATED_AT.plusSeconds(3_600),
                maxAttempts
        );
    }
}
