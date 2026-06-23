package com.docconverter.application.conversion;

public enum DocumentConversionFailureType {
    SOURCE_CONTENT_UNAVAILABLE(false),
    INVALID_SOURCE_DOCUMENT(false),
    CONVERTER_UNAVAILABLE(true),
    CONVERTER_TIMEOUT(true),
    INVALID_CONVERTER_RESPONSE(false),
    INTERNAL_ERROR(false);

    private final boolean retryable;

    DocumentConversionFailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
