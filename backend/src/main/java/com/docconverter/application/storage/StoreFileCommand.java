package com.docconverter.application.storage;

import static com.docconverter.application.support.TextValues.requireNonBlank;

import java.util.Objects;

public record StoreFileCommand(
        StoredFilePurpose purpose,
        String safeFilename,
        String fileExtension,
        String mediaType,
        long sizeBytes,
        FileContentSource contentSource
) {

    public StoreFileCommand {
        Objects.requireNonNull(purpose, "purpose must not be null");
        safeFilename = requireNonBlank(safeFilename, "safeFilename");
        fileExtension = requireNonBlank(fileExtension, "fileExtension");
        mediaType = requireNonBlank(mediaType, "mediaType");
        Objects.requireNonNull(contentSource, "contentSource must not be null");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }

    public StoreFileCommand(
            StoredFilePurpose purpose,
            String safeFilename,
            String fileExtension,
            String mediaType,
            byte[] content
    ) {
        this(
                purpose,
                safeFilename,
                fileExtension,
                mediaType,
                Objects.requireNonNull(content, "content must not be null").length,
                FileContentSource.fromBytes(content)
        );
    }
}
