package com.docconverter.domain.exception;

public class FileTooLargeException extends FileValidationException {

    private final long actualSizeBytes;
    private final long maxSizeBytes;

    public FileTooLargeException(long actualSizeBytes, long maxSizeBytes) {
        super("Uploaded file is too large: " + actualSizeBytes + " bytes, max: " + maxSizeBytes + " bytes");
        this.actualSizeBytes = actualSizeBytes;
        this.maxSizeBytes = maxSizeBytes;
    }

    public long actualSizeBytes() {
        return actualSizeBytes;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }
}
