package com.docconverter.domain.exception;

import com.docconverter.domain.model.ConversionErrorCode;
import java.util.UUID;

public class ConversionResultUnavailableException extends RuntimeException {

    private static final String FALLBACK_ERROR_CODE = "CONVERSION_FAILED";

    private final String errorCode;

    public ConversionResultUnavailableException(
            UUID jobId,
            ConversionErrorCode errorCode,
            String safeErrorMessage
    ) {
        super(message(jobId, safeErrorMessage));
        this.errorCode = errorCode == null ? FALLBACK_ERROR_CODE : errorCode.name();
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String message(UUID jobId, String safeErrorMessage) {
        if (safeErrorMessage == null || safeErrorMessage.isBlank()) {
            return "Conversion failed and no result is available for job: " + jobId;
        }
        return "Conversion failed for job " + jobId + ": " + safeErrorMessage.strip();
    }
}
