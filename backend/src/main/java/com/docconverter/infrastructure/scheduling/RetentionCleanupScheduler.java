package com.docconverter.infrastructure.scheduling;

import com.docconverter.application.port.in.CleanupExpiredConversionUseCase;
import com.docconverter.application.port.out.ConversionJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetentionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final ConversionJobRepository conversionJobRepository;
    private final CleanupExpiredConversionUseCase cleanupExpiredConversionUseCase;
    private final RetentionCleanupProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${doc-converter.retention.cleanup-interval}",
            initialDelayString = "${doc-converter.retention.initial-delay}"
    )
    public void cleanupExpiredConversions() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant cleanupAt = Instant.now(clock);
        List<UUID> candidateIds;
        try {
            candidateIds = conversionJobRepository.findCleanupCandidateIds(
                    cleanupAt,
                    properties.getBatchSize()
            );
        } catch (RuntimeException failure) {
            LOGGER.error("Failed to find retention cleanup candidates", failure);
            return;
        }

        int cleaned = 0;
        for (UUID jobId : candidateIds) {
            try {
                if (cleanupExpiredConversionUseCase.cleanup(jobId, cleanupAt)) {
                    cleaned++;
                }
            } catch (RuntimeException failure) {
                LOGGER.error("Retention cleanup failed for conversion job {}", jobId, failure);
            }
        }

        if (cleaned > 0) {
            LOGGER.info("Retention cleanup completed for {} conversion jobs", cleaned);
        }
    }
}
