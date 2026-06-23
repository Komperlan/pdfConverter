package com.docconverter.infrastructure.scheduling;

import com.docconverter.application.port.in.RecoverStalledConversionUseCase;
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
public class ConversionRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionRecoveryScheduler.class);

    private final ConversionJobRepository conversionJobRepository;
    private final RecoverStalledConversionUseCase recoverStalledConversionUseCase;
    private final ConversionRecoveryProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${doc-converter.recovery.scan-interval}",
            initialDelayString = "${doc-converter.recovery.initial-delay}"
    )
    public void recoverStalledConversions() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant recoveredAt = Instant.now(clock);
        Instant staleBefore = recoveredAt.minus(properties.getStaleTimeout());
        List<UUID> stalledJobIds;
        try {
            stalledJobIds = conversionJobRepository.findStalledProcessingIds(
                    staleBefore,
                    properties.getBatchSize()
            );
        } catch (RuntimeException failure) {
            LOGGER.error("Failed to find stalled conversion jobs", failure);
            return;
        }

        int recovered = 0;
        for (UUID jobId : stalledJobIds) {
            try {
                if (recoverStalledConversionUseCase.recover(jobId, staleBefore, recoveredAt)) {
                    recovered++;
                }
            } catch (RuntimeException failure) {
                LOGGER.error("Failed to recover conversion job {}", jobId, failure);
            }
        }

        if (recovered > 0) {
            LOGGER.info("Recovered {} stalled conversion jobs", recovered);
        }
    }
}
