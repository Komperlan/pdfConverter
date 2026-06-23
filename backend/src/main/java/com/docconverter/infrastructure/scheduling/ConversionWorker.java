package com.docconverter.infrastructure.scheduling;

import com.docconverter.application.port.in.ProcessConversionUseCase;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.domain.model.ConversionJob;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConversionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionWorker.class);

    private final ConversionJobRepository conversionJobRepository;
    private final ProcessConversionUseCase processConversionUseCase;
    private final TaskExecutor taskExecutor;
    private final ConversionWorkerProperties properties;
    private final Clock clock;
    private final Semaphore availableSlots;

    public ConversionWorker(
            ConversionJobRepository conversionJobRepository,
            ProcessConversionUseCase processConversionUseCase,
            @Qualifier("conversionTaskExecutor") TaskExecutor taskExecutor,
            ConversionWorkerProperties properties,
            Clock clock
    ) {
        this.conversionJobRepository = conversionJobRepository;
        this.processConversionUseCase = processConversionUseCase;
        this.taskExecutor = taskExecutor;
        this.properties = properties;
        this.clock = clock;
        this.availableSlots = new Semaphore(properties.getMaxParallelism());
    }

    @Scheduled(
            fixedDelayString = "${doc-converter.worker.poll-interval}",
            initialDelayString = "${doc-converter.worker.initial-delay}"
    )
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }

        int reservedSlots = reserveAvailableSlots();
        if (reservedSlots == 0) {
            return;
        }

        List<ConversionJob> jobs;
        try {
            jobs = conversionJobRepository.claimNextForProcessing(
                    reservedSlots,
                    Instant.now(clock)
            );
        } catch (RuntimeException failure) {
            availableSlots.release(reservedSlots);
            LOGGER.error("Failed to claim conversion jobs", failure);
            return;
        }

        availableSlots.release(reservedSlots - jobs.size());
        for (ConversionJob job : jobs) {
            dispatch(job.getId());
        }
    }

    private int reserveAvailableSlots() {
        int reserved = 0;
        while (reserved < properties.getMaxParallelism() && availableSlots.tryAcquire()) {
            reserved++;
        }
        return reserved;
    }

    private void dispatch(UUID jobId) {
        try {
            taskExecutor.execute(() -> process(jobId));
        } catch (RuntimeException failure) {
            availableSlots.release();
            LOGGER.error(
                    "Failed to dispatch conversion job {}; recovery will retry it",
                    jobId,
                    failure
            );
        }
    }

    private void process(UUID jobId) {
        try {
            processConversionUseCase.process(jobId);
        } catch (RuntimeException failure) {
            LOGGER.error("Conversion worker failed while processing job {}", jobId, failure);
        } finally {
            availableSlots.release();
        }
    }
}
