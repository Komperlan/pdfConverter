package com.docconverter.application.storage;

import static com.docconverter.application.support.TextValues.requireNonBlank;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record StoredFile(
        StoredFilePurpose purpose,
        String storagePath,
        String filename,
        String fileExtension,
        String mediaType,
        long sizeBytes,
        String checksumSha256,
        Instant storedAt
) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    public StoredFile {
        Objects.requireNonNull(purpose, "purpose must not be null");
        storagePath = requireNonBlank(storagePath, "storagePath");
        filename = requireNonBlank(filename, "filename");
        fileExtension = requireNonBlank(fileExtension, "fileExtension");
        mediaType = requireNonBlank(mediaType, "mediaType");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        if (checksumSha256 == null || !SHA_256_PATTERN.matcher(checksumSha256).matches()) {
            throw new IllegalArgumentException("checksumSha256 must contain 64 hexadecimal characters");
        }
        Objects.requireNonNull(storedAt, "storedAt must not be null");
    }
}
