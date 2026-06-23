package com.docconverter.application.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.docconverter.application.conversion.ConvertedDocument;
import com.docconverter.application.conversion.DocumentConversionException;
import com.docconverter.application.conversion.DocumentConversionFailureType;
import com.docconverter.application.port.out.DocumentConverterPort;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.application.processing.ConversionAttemptContext;
import com.docconverter.application.processing.RetryBackoffPolicy;
import com.docconverter.application.storage.FileContentSource;
import com.docconverter.application.storage.StoredFileContent;
import com.docconverter.application.storage.StoredFile;
import com.docconverter.application.storage.StoredFilePurpose;
import com.docconverter.domain.model.ConversionErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversionProcessingServiceTest {

    private ConversionProcessingStateService stateService;
    private FileStoragePort fileStoragePort;
    private DocumentConverterPort documentConverterPort;
    private RetryBackoffPolicy retryBackoffPolicy;
    private ConversionProcessingService service;

    @BeforeEach
    void setUp() {
        stateService = mock(ConversionProcessingStateService.class);
        fileStoragePort = mock(FileStoragePort.class);
        documentConverterPort = mock(DocumentConverterPort.class);
        retryBackoffPolicy = mock(RetryBackoffPolicy.class);
        service = new ConversionProcessingService(
                stateService,
                fileStoragePort,
                documentConverterPort,
                retryBackoffPolicy
        );
    }

    @Test
    void preservesExternalRequestIdWhenRetryableConversionFails() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        byte[] source = "document".getBytes();
        Duration retryDelay = Duration.ofSeconds(5);
        ConversionAttemptContext context = new ConversionAttemptContext(
                jobId,
                attemptId,
                1,
                3,
                "source/document.docx",
                "document.docx",
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                source.length
        );
        when(stateService.startAttempt(jobId)).thenReturn(context);
        when(fileStoragePort.load(context.sourceFilePath())).thenReturn(new StoredFileContent(
                context.sourceFilePath(),
                context.sourceFilename(),
                context.sourceMediaType(),
                source.length,
                FileContentSource.fromBytes(source)
        ));
        when(documentConverterPort.convert(any())).thenThrow(new DocumentConversionException(
                DocumentConversionFailureType.CONVERTER_TIMEOUT,
                "timeout",
                "carbone-request-42",
                null
        ));
        when(retryBackoffPolicy.delayAfterFailure(1)).thenReturn(retryDelay);
        when(stateService.markFailed(
                jobId,
                attemptId,
                "carbone-request-42",
                ConversionErrorCode.CONVERTER_TIMEOUT,
                "Document conversion timed out",
                retryDelay
        )).thenReturn(true);

        service.process(jobId);

        verify(stateService).markFailed(
                jobId,
                attemptId,
                "carbone-request-42",
                ConversionErrorCode.CONVERTER_TIMEOUT,
                "Document conversion timed out",
                retryDelay
        );
    }

    @Test
    void convertsStoresAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        byte[] source = "document".getBytes();
        byte[] pdf = "%PDF-1.7\nresult".getBytes();
        ConversionAttemptContext context = new ConversionAttemptContext(
                jobId,
                attemptId,
                1,
                3,
                "source/document.docx",
                "document.docx",
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                source.length
        );
        when(stateService.startAttempt(jobId)).thenReturn(context);
        when(fileStoragePort.load(context.sourceFilePath())).thenReturn(new StoredFileContent(
                context.sourceFilePath(),
                context.sourceFilename(),
                context.sourceMediaType(),
                source.length,
                FileContentSource.fromBytes(source)
        ));
        when(documentConverterPort.convert(any())).thenReturn(new ConvertedDocument(
                "request-1",
                "result.pdf",
                "application/pdf",
                pdf.length,
                FileContentSource.fromBytes(pdf)
        ));
        when(fileStoragePort.save(any())).thenReturn(new StoredFile(
                StoredFilePurpose.RESULT_PDF,
                "result/result.pdf",
                "result.pdf",
                "pdf",
                "application/pdf",
                pdf.length,
                "a".repeat(64),
                Instant.parse("2026-06-22T10:00:00Z")
        ));

        service.process(jobId);

        verify(stateService).markSucceeded(
                jobId,
                attemptId,
                "request-1",
                "result/result.pdf"
        );
        verify(stateService, never()).markFailed(any(), any(), any(), any(), any(), any());
    }
}
