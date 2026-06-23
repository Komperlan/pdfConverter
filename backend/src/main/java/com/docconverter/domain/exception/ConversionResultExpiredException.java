package com.docconverter.domain.exception;

import java.util.UUID;

public class ConversionResultExpiredException extends RuntimeException {

    public ConversionResultExpiredException(UUID jobId) {
        super("Conversion result has expired for job: " + jobId);
    }
}
