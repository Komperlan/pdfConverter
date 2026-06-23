package com.docconverter.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionRecoveryServiceTest {

    @Mock
    private ConversionJobRepository conversionJobRepository;

    @Mock
    private ConversionAttemptRepository conversionAttemptRepository;

    @Mock
    private RetryBackoffPolicy retryBackoffPolicy;

    private ConversionRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new ConversionRecoveryService(
                conversionJobRepository,
                conversionAttemptRepository,
                retryBackoffPolicy
        );
    }

    @Test
    void recoversStalledJobAndSchedulesRetry() {
        Instant startedAt = Instant.now().plusSeconds(1);
        Instant recoveredAt = startedAt.plusSeconds(180);
        ConversionJob job = processingJob(3, startedAt);
        ConversionAttempt attempt = addStartedAttempt(job, startedAt);
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));
        when(conversionAttemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId()))
                .thenReturn(List.of(attempt));
        when(retryBackoffPolicy.delayAfterFailure(1)).thenReturn(Duration.ofSeconds(5));

        boolean recovered = service.recover(
                job.getId(),
                recoveredAt.minusSeconds(120),
                recoveredAt
        );

        assertThat(recovered).isTrue();
        assertThat(job.getStatus()).isEqualTo(ConversionStatus.CREATED);
        assertThat(job.getNextAttemptAt()).isEqualTo(recoveredAt.plusSeconds(5));
        assertThat(attempt.getStatus()).isEqualTo(ConversionAttemptStatus.FAILED);
        assertThat(attempt.getErrorCode())
                .isEqualTo(ConversionErrorCode.PROCESSING_INTERRUPTED);
        verify(conversionAttemptRepository).save(attempt);
        verify(conversionJobRepository).save(job);
    }

    @Test
    void failsStalledJobWhenAttemptsAreExhausted() {
        Instant startedAt = Instant.now().plusSeconds(1);
        Instant recoveredAt = startedAt.plusSeconds(180);
        ConversionJob job = processingJob(1, startedAt);
        ConversionAttempt attempt = addStartedAttempt(job, startedAt);
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));
        when(conversionAttemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId()))
                .thenReturn(List.of(attempt));

        boolean recovered = service.recover(
                job.getId(),
                recoveredAt.minusSeconds(120),
                recoveredAt
        );

        assertThat(recovered).isTrue();
        assertThat(job.getStatus()).isEqualTo(ConversionStatus.FAILED);
        assertThat(attempt.getStatus()).isEqualTo(ConversionAttemptStatus.FAILED);
        verify(retryBackoffPolicy, never()).delayAfterFailure(1);
    }

    @Test
    void ignoresJobThatIsNotStale() {
        Instant startedAt = Instant.now().plusSeconds(1);
        ConversionJob job = processingJob(3, startedAt);
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));
        when(conversionAttemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId()))
                .thenReturn(List.of());

        boolean recovered = service.recover(
                job.getId(),
                startedAt.minusSeconds(1),
                startedAt.plusSeconds(1)
        );

        assertThat(recovered).isFalse();
        verify(conversionAttemptRepository, never()).save(any());
        verify(retryBackoffPolicy, never()).delayAfterFailure(anyInt());
    }

    @Test
    void ignoresFreshStartedAttemptEvenWhenClaimIsStale() {
        Instant startedAt = Instant.now().plusSeconds(1);
        Instant recoveredAt = startedAt.plusSeconds(180);
        Instant staleBefore = recoveredAt.minusSeconds(120);
        ConversionJob job = processingJob(3, startedAt);
        ConversionAttempt freshAttempt = addStartedAttempt(
                job,
                staleBefore.plusSeconds(1)
        );
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));
        when(conversionAttemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId()))
                .thenReturn(List.of(freshAttempt));

        boolean recovered = service.recover(
                job.getId(),
                staleBefore,
                recoveredAt
        );

        assertThat(recovered).isFalse();
        assertThat(job.getStatus()).isEqualTo(ConversionStatus.PROCESSING);
        assertThat(freshAttempt.getStatus()).isEqualTo(ConversionAttemptStatus.STARTED);
        verify(conversionAttemptRepository, never()).save(any());
        verify(conversionJobRepository, never()).save(job);
    }

    @Test
    void retriesStalledClaimThatHasNoStartedAttempt() {
        Instant startedAt = Instant.now().plusSeconds(1);
        Instant recoveredAt = startedAt.plusSeconds(180);
        ConversionJob job = processingJob(3, startedAt);
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));
        when(conversionAttemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId()))
                .thenReturn(List.of());
        when(retryBackoffPolicy.delayAfterFailure(1)).thenReturn(Duration.ofSeconds(5));

        boolean recovered = service.recover(
                job.getId(),
                recoveredAt.minusSeconds(120),
                recoveredAt
        );

        assertThat(recovered).isTrue();
        assertThat(job.getStatus()).isEqualTo(ConversionStatus.CREATED);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getNextAttemptAt()).isEqualTo(recoveredAt.plusSeconds(5));
        verify(conversionAttemptRepository, never()).save(any());
        verify(conversionJobRepository).save(job);
    }

    private ConversionJob processingJob(int maxAttempts, Instant startedAt) {
        ConversionJob job = ConversionJob.create(
                "report.docx",
                "report.docx",
                "source/report.docx",
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/x-tika-ooxml",
                128,
                "a".repeat(64),
                startedAt,
                startedAt.plusSeconds(3_600),
                maxAttempts
        );
        job.startProcessing(startedAt);
        return job;
    }

    private ConversionAttempt addStartedAttempt(ConversionJob job, Instant startedAt) {
        ConversionAttempt attempt = ConversionAttempt.start(1, startedAt);
        job.registerAttempt(attempt);
        return attempt;
    }
}
