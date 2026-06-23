package com.docconverter.api.dto;

import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionErrorCode;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Instant;
import java.util.UUID;

public record ConversionJobResponse(
        UUID id,
        String originalFilename,
        ConversionStatus status,
        long fileSizeBytes,
        int attemptCount,
        int maxAttempts,
        Instant createdAt,
        Instant updatedAt,
        Instant processingStartedAt,
        Instant processingFinishedAt,
        Instant nextAttemptAt,
        Instant expiresAt,
        ConversionErrorCode errorCode,
        String errorMessage,
        boolean resultAvailable
) {

    public static ConversionJobResponse from(ConversionJob job, Instant now) {
        boolean resultAvailable = job.getStatus() == ConversionStatus.COMPLETED
                && job.getResultFilePath() != null
                && job.getExpiresAt().isAfter(now);

        return new ConversionJobResponse(
                job.getId(),
                job.getOriginalFilename(),
                job.getStatus(),
                job.getFileSizeBytes(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getProcessingStartedAt(),
                job.getProcessingFinishedAt(),
                job.getNextAttemptAt(),
                job.getExpiresAt(),
                job.getLastErrorCode(),
                job.getLastErrorMessage(),
                resultAvailable
        );
    }
}
