package com.docconverter.application.validation;

import static com.docconverter.application.support.TextValues.normalizeOptional;
import static com.docconverter.application.support.TextValues.requireNonBlank;

import com.docconverter.application.storage.FileContentSource;
import java.util.Objects;

public record ValidatedFile(
        String originalFilename,
        String safeFilename,
        String fileExtension,
        String declaredMimeType,
        String detectedMimeType,
        long sizeBytes,
        FileContentSource contentSource
) {

    public ValidatedFile {
        originalFilename = requireNonBlank(originalFilename, "originalFilename");
        safeFilename = requireNonBlank(safeFilename, "safeFilename");
        fileExtension = requireNonBlank(fileExtension, "fileExtension");
        detectedMimeType = requireNonBlank(detectedMimeType, "detectedMimeType");
        Objects.requireNonNull(contentSource, "contentSource must not be null");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        declaredMimeType = normalizeOptional(declaredMimeType);
    }
}
