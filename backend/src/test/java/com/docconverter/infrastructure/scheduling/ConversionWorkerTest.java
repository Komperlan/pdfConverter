package com.docconverter.infrastructure.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.docconverter.application.port.in.ProcessConversionUseCase;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.domain.model.ConversionJob;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class ConversionWorkerTest {

    @Test
    void releasesReservedSlotWhenExecutorRejectsJob() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ProcessConversionUseCase processConversionUseCase = mock(ProcessConversionUseCase.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ConversionWorkerProperties properties = new ConversionWorkerProperties();
        properties.setMaxParallelism(1);
        ConversionJob job = mock(ConversionJob.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(repository.claimNextForProcessing(anyInt(), any(Instant.class)))
                .thenReturn(List.of(job))
                .thenReturn(List.of());
        doThrow(new TaskRejectedException("executor stopped"))
                .when(taskExecutor)
                .execute(any(Runnable.class));
        ConversionWorker worker = new ConversionWorker(
                repository,
                processConversionUseCase,
                taskExecutor,
                properties,
                Clock.systemUTC()
        );

        worker.poll();
        worker.poll();

        verify(repository, times(2)).claimNextForProcessing(anyInt(), any(Instant.class));
        verifyNoInteractions(processConversionUseCase);
    }
}
