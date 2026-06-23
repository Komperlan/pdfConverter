package com.docconverter.application.result;

import static com.docconverter.application.support.TextValues.requireNonBlank;

import com.docconverter.application.storage.FileContentSource;
import java.util.Objects;

public record ConversionResult(
        String filename,
        String mediaType,
        long sizeBytes,
        FileContentSource contentSource
) {

    public static final String PDF_MEDIA_TYPE = "application/pdf";

    public ConversionResult {
        filename = requireNonBlank(filename, "filename");
        if (!PDF_MEDIA_TYPE.equalsIgnoreCase(requireNonBlank(mediaType, "mediaType"))) {
            throw new IllegalArgumentException("mediaType must be application/pdf");
        }
        mediaType = PDF_MEDIA_TYPE;
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        Objects.requireNonNull(contentSource, "contentSource must not be null");
    }
}
