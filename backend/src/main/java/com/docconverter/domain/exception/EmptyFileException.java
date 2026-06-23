package com.docconverter.domain.exception;

public class EmptyFileException extends FileValidationException {

    public EmptyFileException() {
        super("Uploaded file must not be empty");
    }
}
