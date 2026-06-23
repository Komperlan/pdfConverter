package com.docconverter.application.port.in;

import static com.docconverter.application.support.TextValues.normalizeOptional;

import com.docconverter.application.storage.FileContentSource;
import com.docconverter.domain.exception.InvalidUploadRequestException;
import java.util.Objects;

public record SubmitConversionCommand(
        String originalFilename,
        String declaredMimeType,
        long sizeBytes,
        FileContentSource contentSource
) {

    public SubmitConversionCommand {
        requireText(originalFilename, "originalFilename");
        if (contentSource == null) {
            throw new InvalidUploadRequestException("Uploaded file content must be provided");
        }
        if (sizeBytes < 0) {
            throw new InvalidUploadRequestException("Uploaded file size must not be negative");
        }
        declaredMimeType = normalizeOptional(declaredMimeType);
    }

    public SubmitConversionCommand(
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

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidUploadRequestException(fieldName + " must not be blank");
        }
    }
}
