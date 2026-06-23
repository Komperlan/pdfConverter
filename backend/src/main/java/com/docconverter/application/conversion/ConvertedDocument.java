package com.docconverter.application.conversion;

import static com.docconverter.application.support.TextValues.normalizeOptional;
import static com.docconverter.application.support.TextValues.requireNonBlank;

import com.docconverter.application.storage.FileContentSource;
import java.util.Objects;

public record ConvertedDocument(
        String externalRequestId,
        String filename,
        String mediaType,
        long sizeBytes,
        FileContentSource contentSource
) {

    public static final String PDF_MEDIA_TYPE = "application/pdf";

    public ConvertedDocument {
        externalRequestId = normalizeOptional(externalRequestId);
        filename = requireNonBlank(filename, "filename");
        mediaType = requirePdfMediaType(mediaType);
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        Objects.requireNonNull(contentSource, "contentSource must not be null");
    }

    private static String requirePdfMediaType(String value) {
        String normalized = requireNonBlank(value, "mediaType");
        if (!PDF_MEDIA_TYPE.equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("mediaType must be application/pdf");
        }
        return PDF_MEDIA_TYPE;
    }
}
