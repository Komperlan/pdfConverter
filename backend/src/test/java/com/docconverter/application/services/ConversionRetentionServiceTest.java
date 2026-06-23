package com.docconverter.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.domain.model.ConversionAttempt;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionRetentionServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-22T10:00:00Z");

    @Mock
    private ConversionJobRepository conversionJobRepository;

    @Mock
    private FileStoragePort fileStoragePort;

    private ConversionRetentionService service;

    @BeforeEach
    void setUp() {
        service = new ConversionRetentionService(conversionJobRepository, fileStoragePort);
    }

    @Test
    void deletesFilesAndExpiresCompletedJob() {
        ConversionJob job = completedJob();
        Instant cleanupAt = job.getExpiresAt().plusSeconds(1);
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));

        boolean cleaned = service.cleanup(job.getId(), cleanupAt);

        assertThat(cleaned).isTrue();
        assertThat(job.getStatus()).isEqualTo(ConversionStatus.EXPIRED);
        assertThat(job.getCleanupCompletedAt()).isEqualTo(cleanupAt);
        verify(fileStoragePort).delete("source/report.docx");
        verify(fileStoragePort).delete("result/report.pdf");
        verify(conversionJobRepository).save(job);
    }

    @Test
    void skipsProcessingJob() {
        ConversionJob job = newJob();
        job.startProcessing(CREATED_AT.plusSeconds(1));
        when(conversionJobRepository.findByIdForUpdate(job.getId()))
                .thenReturn(Optional.of(job));

        boolean cleaned = service.cleanup(job.getId(), job.getExpiresAt().plusSeconds(1));

        assertThat(cleaned).isFalse();
        verifyNoInteractions(fileStoragePort);
        verify(conversionJobRepository, never()).save(job);
    }

    private ConversionJob completedJob() {
        ConversionJob job = newJob();
        Instant startedAt = CREATED_AT.plusSeconds(1);
        job.startProcessing(startedAt);
        job.registerAttempt(ConversionAttempt.start(1, startedAt));
        job.complete("result/report.pdf", startedAt.plusSeconds(5));
        return job;
    }

    private ConversionJob newJob() {
        return ConversionJob.create(
                "report.docx",
                "report.docx",
                "source/report.docx",
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/x-tika-ooxml",
                128,
                "a".repeat(64),
                CREATED_AT,
                CREATED_AT.plusSeconds(3_600),
                3
        );
    }
}
