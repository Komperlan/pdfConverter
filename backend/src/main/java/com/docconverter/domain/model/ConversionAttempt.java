package com.docconverter.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "conversion_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ConversionAttempt {

    @Id
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    @ToString.Exclude
    private ConversionJob job;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversionAttemptStatus status = ConversionAttemptStatus.STARTED;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "external_request_id", length = 128)
    private String externalRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 64)
    private ConversionErrorCode errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public static ConversionAttempt start(int attemptNumber, Instant startedAt) {
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        Objects.requireNonNull(startedAt, "startedAt must not be null");

        ConversionAttempt attempt = new ConversionAttempt();
        attempt.id = UUID.randomUUID();
        attempt.attemptNumber = attemptNumber;
        attempt.status = ConversionAttemptStatus.STARTED;
        attempt.startedAt = startedAt;
        return attempt;
    }

    public void markSucceeded(String externalRequestId, Instant finishedAt) {
        requireStarted("mark attempt as succeeded");
        String normalizedRequestId = normalizeExternalRequestId(externalRequestId);
        finishAt(finishedAt);
        this.status = ConversionAttemptStatus.SUCCEEDED;
        this.externalRequestId = normalizedRequestId;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(
            ConversionErrorCode errorCode,
            String errorMessage,
            String externalRequestId,
            Instant finishedAt
    ) {
        requireStarted("mark attempt as failed");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
        String normalizedRequestId = normalizeExternalRequestId(externalRequestId);
        finishAt(finishedAt);
        this.status = ConversionAttemptStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.externalRequestId = normalizedRequestId;
    }

    void attachTo(ConversionJob job) {
        Objects.requireNonNull(job, "job must not be null");
        if (this.job != null && this.job != job) {
            throw new IllegalStateException("Conversion attempt already belongs to another job");
        }
        this.job = job;
    }

    private void finishAt(Instant value) {
        Objects.requireNonNull(value, "finishedAt must not be null");
        if (startedAt != null && value.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
        this.finishedAt = value;
        this.durationMs = Duration.between(startedAt, this.finishedAt).toMillis();
    }

    private void requireStarted(String action) {
        if (status != ConversionAttemptStatus.STARTED) {
            throw new IllegalStateException("Cannot " + action + " in status " + status);
        }
    }

    private String normalizeExternalRequestId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("externalRequestId must not exceed 128 characters");
        }
        return normalized;
    }
}
