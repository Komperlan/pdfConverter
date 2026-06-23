package com.docconverter.application.conversion;

import static com.docconverter.application.support.TextValues.requireNonBlank;

import com.docconverter.application.storage.FileContentSource;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ConvertDocumentCommand(
        UUID jobId,
        int attemptNumber,
        String sourceFilename,
        String sourceFileExtension,
        String sourceMediaType,
        long sourceSizeBytes,
        FileContentSource contentSource
) {

    public ConvertDocumentCommand {
        Objects.requireNonNull(jobId, "jobId must not be null");
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        sourceFilename = requireNonBlank(sourceFilename, "sourceFilename");
        sourceFileExtension = normalizeExtension(sourceFileExtension);
        sourceMediaType = requireNonBlank(sourceMediaType, "sourceMediaType");
        if (sourceSizeBytes <= 0) {
            throw new IllegalArgumentException("sourceSizeBytes must be positive");
        }
        Objects.requireNonNull(contentSource, "contentSource must not be null");
    }

    private static String normalizeExtension(String extension) {
        String normalized = requireNonBlank(extension, "sourceFileExtension").toLowerCase(Locale.ROOT);
        normalized = normalized.startsWith(".") ? normalized.substring(1) : normalized;
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("sourceFileExtension must not be blank");
        }
        return normalized;
    }
}
