package com.docconverter.domain.exception;

public class InvalidUploadRequestException extends RuntimeException {

    public InvalidUploadRequestException(String message) {
        super(message);
    }
}
