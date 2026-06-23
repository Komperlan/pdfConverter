package com.docconverter.application.services;

import com.docconverter.application.port.in.RecoverStalledConversionUseCase;
import com.docconverter.application.port.out.ConversionAttemptRepository;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.processing.RetryBackoffPolicy;
import com.docconverter.domain.model.ConversionAttempt;
import com.docconverter.domain.model.ConversionAttemptStatus;
import com.docconverter.domain.model.ConversionErrorCode;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversionRecoveryService implements RecoverStalledConversionUseCase {

    private static final ConversionErrorCode RECOVERY_ERROR_CODE =
            ConversionErrorCode.PROCESSING_INTERRUPTED;
    private static final String RECOVERY_ERROR_MESSAGE =
            "Processing was interrupted before completion";

    private final ConversionJobRepository conversionJobRepository;
    private final ConversionAttemptRepository conversionAttemptRepository;
    private final RetryBackoffPolicy retryBackoffPolicy;

    @Override
    @Transactional
    public boolean recover(UUID jobId, Instant staleBefore, Instant recoveredAt) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(staleBefore, "staleBefore must not be null");
        Objects.requireNonNull(recoveredAt, "recoveredAt must not be null");
        if (recoveredAt.isBefore(staleBefore)) {
            throw new IllegalArgumentException("recoveredAt must not be before staleBefore");
        }

        ConversionJob job = conversionJobRepository.findByIdForUpdate(jobId).orElse(null);
        List<ConversionAttempt> attempts =
                job == null
                        ? List.of()
                        : conversionAttemptRepository.findByJobIdOrderByAttemptNumberAsc(jobId);
        if (!isRecoverable(job, attempts, staleBefore)) {
            return false;
        }

        attempts.stream()
                .filter(attempt -> attempt.getStatus() == ConversionAttemptStatus.STARTED)
                .filter(attempt -> !attempt.getStartedAt().isAfter(staleBefore))
                .forEach(attempt -> {
                    attempt.markFailed(
                            RECOVERY_ERROR_CODE,
                            RECOVERY_ERROR_MESSAGE,
                            null,
                            recoveredAt
                    );
                    conversionAttemptRepository.save(attempt);
                });

        if (job.getAttemptCount() < job.getMaxAttempts()) {
            Duration retryDelay = retryBackoffPolicy.delayAfterFailure(
                    Math.max(1, job.getAttemptCount())
            );
            Instant nextAttemptAt = recoveredAt.plus(retryDelay);
            if (nextAttemptAt.isBefore(job.getExpiresAt())) {
                job.scheduleRetry(
                        RECOVERY_ERROR_CODE,
                        RECOVERY_ERROR_MESSAGE,
                        recoveredAt,
                        nextAttemptAt
                );
                conversionJobRepository.save(job);
                return true;
            }
        }

        job.fail(RECOVERY_ERROR_CODE, RECOVERY_ERROR_MESSAGE, recoveredAt);
        conversionJobRepository.save(job);
        return true;
    }

    private boolean isRecoverable(
            ConversionJob job,
            List<ConversionAttempt> attempts,
            Instant staleBefore
    ) {
        if (job == null || job.getStatus() != ConversionStatus.PROCESSING) {
            return false;
        }

        List<ConversionAttempt> startedAttempts = attempts.stream()
                .filter(attempt -> attempt.getStatus() == ConversionAttemptStatus.STARTED)
                .toList();
        if (startedAttempts.stream()
                .anyMatch(attempt -> attempt.getStartedAt().isAfter(staleBefore))) {
            return false;
        }
        if (!startedAttempts.isEmpty()) {
            return true;
        }

        return job.getProcessingStartedAt() == null
                || !job.getProcessingStartedAt().isAfter(staleBefore);
    }
}
