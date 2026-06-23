package com.docconverter.domain.exception;

public class UnsupportedFileTypeException extends FileValidationException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
