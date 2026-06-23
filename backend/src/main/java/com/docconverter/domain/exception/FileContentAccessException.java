package com.docconverter.domain.exception;

public class FileContentAccessException extends RuntimeException {

    public FileContentAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
