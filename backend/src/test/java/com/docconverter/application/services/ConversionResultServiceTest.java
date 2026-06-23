package com.docconverter.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.application.storage.FileContentSource;
import com.docconverter.application.storage.StoredFileContent;
import com.docconverter.domain.exception.ConversionJobNotFoundException;
import com.docconverter.domain.exception.ConversionResultExpiredException;
import com.docconverter.domain.exception.ConversionResultNotReadyException;
import com.docconverter.domain.exception.ConversionResultUnavailableException;
import com.docconverter.domain.model.ConversionErrorCode;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ConversionResultServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ConversionJobRepository conversionJobRepository;
    private FileStoragePort fileStoragePort;
    private ConversionResultService service;

    @BeforeEach
    void setUp() {
        conversionJobRepository = mock(ConversionJobRepository.class);
        fileStoragePort = mock(FileStoragePort.class);
        service = new ConversionResultService(conversionJobRepository, fileStoragePort, CLOCK);
    }

    @Test
    void returnsNotFoundWhenJobDoesNotExist() {
        UUID jobId = UUID.randomUUID();
        when(conversionJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult(jobId))
                .isInstanceOf(ConversionJobNotFoundException.class);

        verifyNoInteractions(fileStoragePort);
    }

    @ParameterizedTest
    @EnumSource(value = ConversionStatus.class, names = {"CREATED", "PROCESSING"})
    void returnsConflictStateWhenResultIsNotReady(ConversionStatus status) {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(status, NOW.plusSeconds(3_600));
        when(conversionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getResult(jobId))
                .isInstanceOf(ConversionResultNotReadyException.class)
                .hasMessageContaining(status.name());

        verifyNoInteractions(fileStoragePort);
    }

    @Test
    void exposesSafeFailureCodeAndMessageForFailedJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(ConversionStatus.FAILED, NOW.plusSeconds(3_600));
        when(job.getLastErrorCode()).thenReturn(ConversionErrorCode.CONVERTER_TIMEOUT);
        when(job.getLastErrorMessage()).thenReturn("Document conversion timed out");
        when(conversionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getResult(jobId))
                .isInstanceOfSatisfying(
                        ConversionResultUnavailableException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isEqualTo("CONVERTER_TIMEOUT");
                            assertThat(exception.getMessage())
                                    .contains("Document conversion timed out");
                        }
                );

        verifyNoInteractions(fileStoragePort);
    }

    @Test
    void returnsGoneForExpiredStatus() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(ConversionStatus.EXPIRED, NOW.plusSeconds(3_600));
        when(conversionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getResult(jobId))
                .isInstanceOf(ConversionResultExpiredException.class);

        verifyNoInteractions(fileStoragePort);
    }

    @Test
    void expirationTakesPriorityOverFailedStatus() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(ConversionStatus.FAILED, NOW.minusSeconds(1));
        when(conversionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getResult(jobId))
                .isInstanceOf(ConversionResultExpiredException.class);

        verifyNoInteractions(fileStoragePort);
    }

    @Test
    void completedJobWithoutResultPathIsAnInternalStateError() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(ConversionStatus.COMPLETED, NOW.plusSeconds(3_600));
        when(job.getResultFilePath()).thenReturn(null);
        when(conversionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getResult(jobId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Completed conversion job has no result file path");

        verifyNoInteractions(fileStoragePort);
    }

    @Test
    void loadsCompletedPdf() throws Exception {
        UUID jobId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7\nresult".getBytes();
        ConversionJob job = job(ConversionStatus.COMPLETED, NOW.plusSeconds(3_600));
        when(job.getResultFilePath()).thenReturn("result/2026/06/22/report.pdf");
        when(job.getSafeOriginalFilename()).thenReturn("report.docx");
        when(conversionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileStoragePort.load("result/2026/06/22/report.pdf"))
                .thenReturn(new StoredFileContent(
                        "result/2026/06/22/report.pdf",
                        "report.pdf",
                        "application/pdf",
                        pdf.length,
                        FileContentSource.fromBytes(pdf)
                ));

        var result = service.getResult(jobId);

        assertThat(result.filename()).isEqualTo("report.pdf");
        assertThat(result.mediaType()).isEqualTo("application/pdf");
        assertThat(result.sizeBytes()).isEqualTo(pdf.length);
        try (var input = result.contentSource().openStream()) {
            assertThat(input.readAllBytes()).isEqualTo(pdf);
        }
    }

    private ConversionJob job(ConversionStatus status, Instant expiresAt) {
        ConversionJob job = mock(ConversionJob.class);
        when(job.getStatus()).thenReturn(status);
        when(job.getExpiresAt()).thenReturn(expiresAt);
        return job;
    }
}
