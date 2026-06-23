package com.docconverter.application.services;

import com.docconverter.application.conversion.ConvertDocumentCommand;
import com.docconverter.application.conversion.ConvertedDocument;
import com.docconverter.application.conversion.DocumentConversionException;
import com.docconverter.application.port.in.ProcessConversionUseCase;
import com.docconverter.application.port.out.DocumentConverterPort;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.application.processing.ConversionAttemptContext;
import com.docconverter.application.processing.RetryBackoffPolicy;
import com.docconverter.application.storage.StoreFileCommand;
import com.docconverter.application.storage.StoredFile;
import com.docconverter.application.storage.StoredFileContent;
import com.docconverter.application.storage.StoredFilePurpose;
import com.docconverter.domain.exception.FileContentAccessException;
import com.docconverter.domain.exception.FileStorageException;
import com.docconverter.domain.model.ConversionErrorCode;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversionProcessingService implements ProcessConversionUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionProcessingService.class);

    private final ConversionProcessingStateService stateService;
    private final FileStoragePort fileStoragePort;
    private final DocumentConverterPort documentConverterPort;
    private final RetryBackoffPolicy retryBackoffPolicy;

    @Override
    public void process(UUID jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");

        ConversionAttemptContext context = stateService.startAttempt(jobId);
        StoredConversionResult storedResult = null;

        try {
            storedResult = convertAndStore(context);
            stateService.markSucceeded(
                    context.jobId(),
                    context.attemptId(),
                    storedResult.externalRequestId(),
                    storedResult.storagePath()
            );
            LOGGER.info(
                    "Conversion job {} completed on attempt {}",
                    context.jobId(),
                    context.attemptNumber()
            );
        } catch (RuntimeException failure) {
            if (storedResult != null) {
                deleteResultAfterFailure(context.jobId(), storedResult.storagePath(), failure);
            }

            ProcessingFailure processingFailure = classifyFailure(failure);
            Duration retryDelay = processingFailure.retryable()
                    && context.attemptNumber() < context.maxAttempts()
                    ? retryBackoffPolicy.delayAfterFailure(context.attemptNumber())
                    : null;
            boolean retryScheduled = stateService.markFailed(
                    context.jobId(),
                    context.attemptId(),
                    processingFailure.externalRequestId(),
                    processingFailure.errorCode(),
                    processingFailure.safeMessage(),
                    retryDelay
            );

            if (retryScheduled) {
                LOGGER.warn(
                        "Conversion job {} scheduled for retry after {} ms; attempt {} failed with code {}",
                        context.jobId(),
                        retryDelay.toMillis(),
                        context.attemptNumber(),
                        processingFailure.errorCode()
                );
            } else {
                LOGGER.warn(
                        "Conversion job {} failed on attempt {} with code {}",
                        context.jobId(),
                        context.attemptNumber(),
                        processingFailure.errorCode()
                );
            }
        }
    }

    private StoredConversionResult convertAndStore(ConversionAttemptContext context) {
        StoredFileContent source = fileStoragePort.load(context.sourceFilePath());
        if (source.sizeBytes() != context.sourceSizeBytes()) {
            throw new FileStorageException("Stored source document size does not match job metadata");
        }

        ConvertedDocument converted = documentConverterPort.convert(new ConvertDocumentCommand(
                context.jobId(),
                context.attemptNumber(),
                context.sourceFilename(),
                context.fileExtension(),
                context.sourceMediaType(),
                source.sizeBytes(),
                source.contentSource()
        ));
        StoredFile result = fileStoragePort.save(new StoreFileCommand(
                StoredFilePurpose.RESULT_PDF,
                converted.filename(),
                "pdf",
                converted.mediaType(),
                converted.sizeBytes(),
                converted.contentSource()
        ));
        return new StoredConversionResult(
                result.storagePath(),
                converted.externalRequestId()
        );
    }

    private ProcessingFailure classifyFailure(RuntimeException failure) {
        if (failure instanceof DocumentConversionException conversionFailure) {
            String externalRequestId = conversionFailure.getExternalRequestId();
            return switch (conversionFailure.getFailureType()) {
                case SOURCE_CONTENT_UNAVAILABLE -> new ProcessingFailure(
                        ConversionErrorCode.STORAGE_ERROR,
                        "Source document is unavailable",
                        false,
                        externalRequestId
                );
                case INVALID_SOURCE_DOCUMENT -> new ProcessingFailure(
                        ConversionErrorCode.CORRUPTED_DOCUMENT,
                        "Source document could not be converted",
                        false,
                        externalRequestId
                );
                case CONVERTER_UNAVAILABLE -> new ProcessingFailure(
                        ConversionErrorCode.CONVERTER_UNAVAILABLE,
                        "Document converter is unavailable",
                        conversionFailure.isRetryable(),
                        externalRequestId
                );
                case CONVERTER_TIMEOUT -> new ProcessingFailure(
                        ConversionErrorCode.CONVERTER_TIMEOUT,
                        "Document conversion timed out",
                        conversionFailure.isRetryable(),
                        externalRequestId
                );
                case INVALID_CONVERTER_RESPONSE -> new ProcessingFailure(
                        ConversionErrorCode.INTERNAL_ERROR,
                        "Document converter returned an invalid response",
                        false,
                        externalRequestId
                );
                case INTERNAL_ERROR -> new ProcessingFailure(
                        ConversionErrorCode.INTERNAL_ERROR,
                        "Document converter request could not be completed",
                        false,
                        externalRequestId
                );
            };
        }
        if (failure instanceof FileStorageException
                || failure instanceof FileContentAccessException) {
            return new ProcessingFailure(
                    ConversionErrorCode.STORAGE_ERROR,
                    "File storage operation failed",
                    false,
                    null
            );
        }
        return new ProcessingFailure(
                ConversionErrorCode.INTERNAL_ERROR,
                "Unexpected conversion processing error",
                false,
                null
        );
    }

    private void deleteResultAfterFailure(
            UUID jobId,
            String storagePath,
            RuntimeException originalFailure
    ) {
        try {
            fileStoragePort.delete(storagePath);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            LOGGER.error(
                    "Failed to clean up result for conversion job {} after processing failure",
                    jobId,
                    cleanupFailure
            );
        }
    }

    private record StoredConversionResult(
            String storagePath,
            String externalRequestId
    ) {
    }

    private record ProcessingFailure(
            ConversionErrorCode errorCode,
            String safeMessage,
            boolean retryable,
            String externalRequestId
    ) {
    }
}
