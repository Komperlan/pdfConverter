package com.docconverter.application.conversion;

import static com.docconverter.application.support.TextValues.normalizeOptional;
import static com.docconverter.application.support.TextValues.requireNonBlank;

import java.util.Objects;

public class DocumentConversionException extends RuntimeException {

    private final DocumentConversionFailureType failureType;
    private final String externalRequestId;

    public DocumentConversionException(
            DocumentConversionFailureType failureType,
            String message
    ) {
        this(failureType, message, null, null);
    }

    public DocumentConversionException(
            DocumentConversionFailureType failureType,
            String message,
            Throwable cause
    ) {
        this(failureType, message, null, cause);
    }

    public DocumentConversionException(
            DocumentConversionFailureType failureType,
            String message,
            String externalRequestId,
            Throwable cause
    ) {
        super(requireNonBlank(message, "message"), cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
        this.externalRequestId = normalizeOptional(externalRequestId);
    }

    public DocumentConversionFailureType getFailureType() {
        return failureType;
    }

    public String getExternalRequestId() {
        return externalRequestId;
    }

    public boolean isRetryable() {
        return failureType.isRetryable();
    }
}
