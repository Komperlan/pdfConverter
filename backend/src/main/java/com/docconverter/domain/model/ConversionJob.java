package com.docconverter.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conversion_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ConversionJob {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    @Id
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "safe_original_filename", nullable = false, length = 255)
    private String safeOriginalFilename;

    @Column(name = "source_file_path", nullable = false, columnDefinition = "text")
    private String sourceFilePath;

    @Column(name = "result_file_path", columnDefinition = "text")
    private String resultFilePath;

    @Column(name = "file_extension", nullable = false, length = 8)
    private String fileExtension;

    @Column(name = "declared_mime_type", length = 128)
    private String declaredMimeType;

    @Column(name = "detected_mime_type", nullable = false, length = 128)
    private String detectedMimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversionStatus status = ConversionStatus.CREATED;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_finished_at")
    private Instant processingFinishedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "cleanup_completed_at")
    private Instant cleanupCompletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_code", length = 64)
    private ConversionErrorCode lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    public static ConversionJob create(
            String originalFilename,
            String safeOriginalFilename,
            String sourceFilePath,
            String fileExtension,
            String declaredMimeType,
            String detectedMimeType,
            long fileSizeBytes,
            String checksumSha256,
            Instant createdAt,
            Instant expiresAt,
            int maxAttempts
    ) {
        requireText(originalFilename, "originalFilename", 255);
        requireText(safeOriginalFilename, "safeOriginalFilename", 255);
        requireText(sourceFilePath, "sourceFilePath", Integer.MAX_VALUE);
        requireText(fileExtension, "fileExtension", 8);
        requireOptionalText(declaredMimeType, "declaredMimeType", 128);
        requireText(detectedMimeType, "detectedMimeType", 128);
        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("fileSizeBytes must be positive");
        }
        if (checksumSha256 == null || !SHA_256_PATTERN.matcher(checksumSha256).matches()) {
            throw new IllegalArgumentException("checksumSha256 must contain 64 hexadecimal characters");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }

        ConversionJob job = new ConversionJob();
        job.id = UUID.randomUUID();
        job.originalFilename = originalFilename;
        job.safeOriginalFilename = safeOriginalFilename;
        job.sourceFilePath = sourceFilePath;
        job.fileExtension = fileExtension;
        job.declaredMimeType = declaredMimeType;
        job.detectedMimeType = detectedMimeType;
        job.fileSizeBytes = fileSizeBytes;
        job.checksumSha256 = checksumSha256;
        job.status = ConversionStatus.CREATED;
        job.maxAttempts = maxAttempts;
        job.createdAt = createdAt;
        job.updatedAt = createdAt;
        job.nextAttemptAt = createdAt;
        job.expiresAt = expiresAt;
        return job;
    }

    public void startProcessing(Instant startedAt) {
        requireStatus(ConversionStatus.CREATED, "start processing");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (nextAttemptAt != null && nextAttemptAt.isAfter(startedAt)) {
            throw new IllegalStateException("Conversion job is not ready for processing");
        }
        this.status = ConversionStatus.PROCESSING;
        this.processingStartedAt = startedAt;
        this.processingFinishedAt = null;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = startedAt;
    }

    public void complete(String resultFilePath, Instant finishedAt) {
        requireStatus(ConversionStatus.PROCESSING, "complete");
        requireText(resultFilePath, "resultFilePath", Integer.MAX_VALUE);
        validateFinishedAt(finishedAt);
        this.status = ConversionStatus.COMPLETED;
        this.resultFilePath = resultFilePath;
        this.processingFinishedAt = finishedAt;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = finishedAt;
    }

    public void fail(ConversionErrorCode errorCode, String errorMessage, Instant finishedAt) {
        requireStatus(ConversionStatus.PROCESSING, "fail");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        requireText(errorMessage, "errorMessage", Integer.MAX_VALUE);
        validateFinishedAt(finishedAt);
        this.status = ConversionStatus.FAILED;
        this.processingFinishedAt = finishedAt;
        this.nextAttemptAt = null;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.updatedAt = finishedAt;
    }

    public void scheduleRetry(
            ConversionErrorCode errorCode,
            String errorMessage,
            Instant scheduledAt,
            Instant nextAttemptAt
    ) {
        requireStatus(ConversionStatus.PROCESSING, "schedule retry");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        requireText(errorMessage, "errorMessage", Integer.MAX_VALUE);
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        if (processingStartedAt != null && scheduledAt.isBefore(processingStartedAt)) {
            throw new IllegalArgumentException("scheduledAt must not be before processingStartedAt");
        }
        if (!nextAttemptAt.isAfter(scheduledAt)) {
            throw new IllegalArgumentException("nextAttemptAt must be after scheduledAt");
        }
        if (!expiresAt.isAfter(nextAttemptAt)) {
            throw new IllegalArgumentException("nextAttemptAt must be before expiresAt");
        }

        this.status = ConversionStatus.CREATED;
        this.processingStartedAt = null;
        this.processingFinishedAt = null;
        this.nextAttemptAt = nextAttemptAt;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.updatedAt = scheduledAt;
    }

    public void expire(Instant expiredAt) {
        Objects.requireNonNull(expiredAt, "expiredAt must not be null");
        if (status == ConversionStatus.PROCESSING || status == ConversionStatus.EXPIRED) {
            throw new IllegalStateException("Cannot expire conversion job in status " + status);
        }
        if (expiresAt != null && expiredAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("expiredAt must not be before expiresAt");
        }
        this.status = ConversionStatus.EXPIRED;
        this.expiredAt = expiredAt;
        this.nextAttemptAt = null;
        this.updatedAt = expiredAt;
    }

    public void markCleanupCompleted(Instant completedAt) {
        requireStatus(ConversionStatus.EXPIRED, "complete retention cleanup");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (expiredAt != null && completedAt.isBefore(expiredAt)) {
            throw new IllegalArgumentException("completedAt must not be before expiredAt");
        }
        if (cleanupCompletedAt != null) {
            throw new IllegalStateException("Retention cleanup is already completed");
        }
        this.cleanupCompletedAt = completedAt;
        this.updatedAt = completedAt;
    }

    public void registerAttempt(ConversionAttempt attempt) {
        requireStatus(ConversionStatus.PROCESSING, "register conversion attempt");
        Objects.requireNonNull(attempt, "attempt must not be null");
        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException("Maximum conversion attempts reached");
        }
        int expectedAttemptNumber = attemptCount + 1;
        if (attempt.getAttemptNumber() != expectedAttemptNumber) {
            throw new IllegalArgumentException("attemptNumber must be " + expectedAttemptNumber);
        }
        attempt.attachTo(this);
        attemptCount = expectedAttemptNumber;
        updatedAt = attempt.getStartedAt();
    }

    private void requireStatus(ConversionStatus requiredStatus, String action) {
        if (status != requiredStatus) {
            throw new IllegalStateException(
                    "Cannot " + action + " conversion job in status " + status
            );
        }
    }

    private void validateFinishedAt(Instant finishedAt) {
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        if (processingStartedAt != null && finishedAt.isBefore(processingStartedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before processingStartedAt");
        }
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length " + maxLength);
        }
    }

    private static void requireOptionalText(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length " + maxLength);
        }
    }
}
