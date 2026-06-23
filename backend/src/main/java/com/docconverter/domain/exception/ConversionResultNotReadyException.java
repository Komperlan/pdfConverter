package com.docconverter.domain.exception;

import com.docconverter.domain.model.ConversionStatus;
import java.util.UUID;

public class ConversionResultNotReadyException extends RuntimeException {

    public ConversionResultNotReadyException(UUID jobId, ConversionStatus status) {
        super("Conversion result is not ready for job " + jobId + ", current status: " + status);
    }
}
