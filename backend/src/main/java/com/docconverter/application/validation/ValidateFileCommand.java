package com.docconverter.application.validation;

import static com.docconverter.application.support.TextValues.normalizeOptional;
import static com.docconverter.application.support.TextValues.requireNonBlank;

import com.docconverter.application.storage.FileContentSource;
import java.util.Objects;

public record ValidateFileCommand(
        String originalFilename,
        String declaredMimeType,
        long sizeBytes,
        FileContentSource contentSource
) {

    public ValidateFileCommand {
        originalFilename = requireNonBlank(originalFilename, "originalFilename");
        Objects.requireNonNull(contentSource, "contentSource must not be null");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        declaredMimeType = normalizeOptional(declaredMimeType);
    }

    public ValidateFileCommand(
            String originalFilename,
            String declaredMimeType,
            byte[] content
    ) {
        this(
                originalFilename,
                declaredMimeType,
                Objects.requireNonNull(content, "content must not be null").length,
                FileContentSource.fromBytes(content)
        );
    }
}
