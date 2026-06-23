package com.docconverter.application.storage;

import static com.docconverter.application.support.TextValues.requireNonBlank;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record StoredFileContent(
        String storagePath,
        String filename,
        String mediaType,
        long sizeBytes,
        FileContentSource contentSource
) {

    public StoredFileContent {
        storagePath = requireNonBlank(storagePath, "storagePath");
        filename = requireNonBlank(filename, "filename");
        mediaType = requireNonBlank(mediaType, "mediaType");
        Objects.requireNonNull(contentSource, "contentSource must not be null");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }

    public InputStream openStream() throws IOException {
        return contentSource.openStream();
    }
}
