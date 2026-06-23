package com.docconverter.application.processing;

import static com.docconverter.application.support.TextValues.requireNonBlank;

import java.util.Objects;
import java.util.UUID;

public record ConversionAttemptContext(
        UUID jobId,
        UUID attemptId,
        int attemptNumber,
        int maxAttempts,
        String sourceFilePath,
        String sourceFilename,
        String fileExtension,
        String sourceMediaType,
        long sourceSizeBytes
) {

    public ConversionAttemptContext {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        if (maxAttempts < attemptNumber) {
            throw new IllegalArgumentException("maxAttempts must not be lower than attemptNumber");
        }
        sourceFilePath = requireNonBlank(sourceFilePath, "sourceFilePath");
        sourceFilename = requireNonBlank(sourceFilename, "sourceFilename");
        fileExtension = requireNonBlank(fileExtension, "fileExtension");
        sourceMediaType = requireNonBlank(sourceMediaType, "sourceMediaType");
        if (sourceSizeBytes <= 0) {
            throw new IllegalArgumentException("sourceSizeBytes must be positive");
        }
    }
}
