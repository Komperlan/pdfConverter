package com.docconverter.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.docconverter.application.port.out.ConversionAttemptRepository;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.domain.model.ConversionAttempt;
import com.docconverter.domain.model.ConversionAttemptStatus;
import com.docconverter.domain.model.ConversionErrorCode;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionProcessingStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T10:00:00Z");

    @Mock
    private ConversionJobRepository conversionJobRepository;

    @Mock
    private ConversionAttemptRepository conversionAttemptRepository;

    private ConversionProcessingStateService service;

    @BeforeEach
    void setUp() {
        service = new ConversionProcessingStateService(
                conversionJobRepository,
                conversionAttemptRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void startsAndPersistsAttempt() {
        ConversionJob job = processingJob();
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));

        var context = service.startAttempt(job.getId());

        ArgumentCaptor<ConversionAttempt> attemptCaptor =
                ArgumentCaptor.forClass(ConversionAttempt.class);
        verify(conversionAttemptRepository).save(attemptCaptor.capture());
        ConversionAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getStatus()).isEqualTo(ConversionAttemptStatus.STARTED);
        assertThat(attempt.getStartedAt()).isEqualTo(NOW);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(context.attemptId()).isEqualTo(attempt.getId());
        assertThat(context.sourceFilePath()).isEqualTo("source/report.docx");
        verify(conversionJobRepository).save(job);
    }

    @Test
    void schedulesRetryAfterFailedAttempt() {
        ConversionJob job = processingJob();
        ConversionAttempt attempt = ConversionAttempt.start(1, NOW.minusSeconds(1));
        job.registerAttempt(attempt);
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));
        when(conversionAttemptRepository.findByIdAndJobId(attempt.getId(), job.getId()))
                .thenReturn(Optional.of(attempt));

        boolean retryScheduled = service.markFailed(
                job.getId(),
                attempt.getId(),
                "request-1",
                ConversionErrorCode.CONVERTER_TIMEOUT,
                "Document conversion timed out",
                Duration.ofSeconds(5)
        );

        assertThat(retryScheduled).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(ConversionAttemptStatus.FAILED);
        assertThat(attempt.getExternalRequestId()).isEqualTo("request-1");
        assertThat(job.getStatus()).isEqualTo(ConversionStatus.CREATED);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        verify(conversionAttemptRepository).save(attempt);
        verify(conversionJobRepository).save(job);
    }

    private ConversionJob processingJob() {
        ConversionJob job = ConversionJob.create(
                "report.docx",
                "report.docx",
                "source/report.docx",
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/x-tika-ooxml",
                128,
                "a".repeat(64),
                NOW.minusSeconds(10),
                NOW.plusSeconds(3_600),
                3
        );
        job.startProcessing(NOW.minusSeconds(2));
        return job;
    }
}
