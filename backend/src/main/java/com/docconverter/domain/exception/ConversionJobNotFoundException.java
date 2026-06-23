package com.docconverter.domain.exception;

import java.util.UUID;

public class ConversionJobNotFoundException extends RuntimeException {

    public ConversionJobNotFoundException(UUID id) {
        super("Conversion job not found: " + id);
    }
}
