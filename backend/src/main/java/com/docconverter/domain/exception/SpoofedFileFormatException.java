package com.docconverter.domain.exception;

public class SpoofedFileFormatException extends FileValidationException {

    public SpoofedFileFormatException(String expectedExtension, String detectedMimeType) {
        super("File content does not match ." + expectedExtension + " format, detected MIME type: " + detectedMimeType);
    }
}
