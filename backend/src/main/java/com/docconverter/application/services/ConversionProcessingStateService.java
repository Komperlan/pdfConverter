package com.docconverter.application.services;

import com.docconverter.application.port.out.ConversionAttemptRepository;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.processing.ConversionAttemptContext;
import com.docconverter.domain.exception.ConversionJobNotFoundException;
import com.docconverter.domain.model.ConversionAttempt;
import com.docconverter.domain.model.ConversionErrorCode;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversionProcessingStateService {

    private static final int MAX_EXTERNAL_REQUEST_ID_LENGTH = 128;

    private final ConversionJobRepository conversionJobRepository;
    private final ConversionAttemptRepository conversionAttemptRepository;
    private final Clock clock;

    @Transactional
    public ConversionAttemptContext startAttempt(UUID jobId) {
        ConversionJob job = getJobForUpdate(jobId);
        if (job.getStatus() != ConversionStatus.PROCESSING) {
            throw new IllegalStateException("Conversion job is not processing: " + jobId);
        }

        ConversionAttempt attempt = ConversionAttempt.start(
                job.getAttemptCount() + 1,
                Instant.now(clock)
        );
        job.registerAttempt(attempt);
        conversionAttemptRepository.save(attempt);
        conversionJobRepository.save(job);

        return new ConversionAttemptContext(
                job.getId(),
                attempt.getId(),
                attempt.getAttemptNumber(),
                job.getMaxAttempts(),
                job.getSourceFilePath(),
                job.getSafeOriginalFilename(),
                job.getFileExtension(),
                job.getDetectedMimeType(),
                job.getFileSizeBytes()
        );
    }

    @Transactional
    public void markSucceeded(
            UUID jobId,
            UUID attemptId,
            String externalRequestId,
            String resultFilePath
    ) {
        ConversionJob job = getJobForUpdate(jobId);
        ConversionAttempt attempt = getAttempt(job, attemptId);
        Instant finishedAt = Instant.now(clock);

        attempt.markSucceeded(normalizeExternalRequestId(externalRequestId), finishedAt);
        job.complete(resultFilePath, finishedAt);
        conversionAttemptRepository.save(attempt);
        conversionJobRepository.save(job);
    }

    @Transactional
    public boolean markFailed(
            UUID jobId,
            UUID attemptId,
            String externalRequestId,
            ConversionErrorCode errorCode,
            String errorMessage,
            Duration retryDelay
    ) {
        ConversionJob job = getJobForUpdate(jobId);
        ConversionAttempt attempt = getAttempt(job, attemptId);
        Instant finishedAt = Instant.now(clock);

        attempt.markFailed(
                errorCode,
                errorMessage,
                normalizeExternalRequestId(externalRequestId),
                finishedAt
        );
        boolean retryScheduled = false;
        if (canScheduleRetry(job, retryDelay)) {
            Instant nextAttemptAt = finishedAt.plus(retryDelay);
            if (nextAttemptAt.isBefore(job.getExpiresAt())) {
                job.scheduleRetry(errorCode, errorMessage, finishedAt, nextAttemptAt);
                retryScheduled = true;
            } else {
                job.fail(errorCode, errorMessage, finishedAt);
            }
        } else {
            job.fail(errorCode, errorMessage, finishedAt);
        }
        conversionAttemptRepository.save(attempt);
        conversionJobRepository.save(job);
        return retryScheduled;
    }

    private boolean canScheduleRetry(ConversionJob job, Duration retryDelay) {
        return retryDelay != null
                && !retryDelay.isZero()
                && !retryDelay.isNegative()
                && job.getAttemptCount() < job.getMaxAttempts();
    }

    private ConversionJob getJobForUpdate(UUID jobId) {
        return conversionJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ConversionJobNotFoundException(jobId));
    }

    private ConversionAttempt getAttempt(ConversionJob job, UUID attemptId) {
        return conversionAttemptRepository.findByIdAndJobId(attemptId, job.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Conversion attempt does not exist for job: " + attemptId
                ));
    }

    private String normalizeExternalRequestId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() <= MAX_EXTERNAL_REQUEST_ID_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_EXTERNAL_REQUEST_ID_LENGTH);
    }
}
