package com.docconverter.domain.model;

public enum ConversionErrorCode {
    CORRUPTED_DOCUMENT,
    CONVERTER_UNAVAILABLE,
    CONVERTER_TIMEOUT,
    PROCESSING_INTERRUPTED,
    STORAGE_ERROR,
    INTERNAL_ERROR
}
