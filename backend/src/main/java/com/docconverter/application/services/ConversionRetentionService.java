package com.docconverter.application.services;

import com.docconverter.application.port.in.CleanupExpiredConversionUseCase;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversionRetentionService implements CleanupExpiredConversionUseCase {

    private final ConversionJobRepository conversionJobRepository;
    private final FileStoragePort fileStoragePort;

    @Override
    @Transactional
    public boolean cleanup(UUID jobId, Instant cleanupAt) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(cleanupAt, "cleanupAt must not be null");

        ConversionJob job = conversionJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getCleanupCompletedAt() != null) {
            return false;
        }
        if (job.getStatus() == ConversionStatus.PROCESSING) {
            return false;
        }
        if (job.getStatus() != ConversionStatus.EXPIRED) {
            if (job.getExpiresAt().isAfter(cleanupAt)) {
                return false;
            }
            job.expire(cleanupAt);
        }

        deleteIfPresent(job.getSourceFilePath());
        if (!Objects.equals(job.getSourceFilePath(), job.getResultFilePath())) {
            deleteIfPresent(job.getResultFilePath());
        }

        job.markCleanupCompleted(cleanupAt);
        conversionJobRepository.save(job);
        return true;
    }

    private void deleteIfPresent(String storagePath) {
        if (storagePath != null && !storagePath.isBlank()) {
            fileStoragePort.delete(storagePath);
        }
    }
}
