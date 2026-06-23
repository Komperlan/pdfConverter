package com.docconverter.api.errors;

import com.docconverter.api.dto.ApiErrorResponse;
import com.docconverter.domain.exception.ConversionJobNotFoundException;
import com.docconverter.domain.exception.ConversionResultExpiredException;
import com.docconverter.domain.exception.ConversionResultNotReadyException;
import com.docconverter.domain.exception.ConversionResultUnavailableException;
import com.docconverter.domain.exception.EmptyFileException;
import com.docconverter.domain.exception.FileContentAccessException;
import com.docconverter.domain.exception.FileStorageException;
import com.docconverter.domain.exception.FileTooLargeException;
import com.docconverter.domain.exception.FileValidationException;
import com.docconverter.domain.exception.InvalidUploadRequestException;
import com.docconverter.domain.exception.SpoofedFileFormatException;
import com.docconverter.domain.exception.UnsupportedFileTypeException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    @ExceptionHandler({
            EmptyFileException.class,
            InvalidUploadRequestException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, badRequestMessage(exception), request);
    }

    @ExceptionHandler({
            UnsupportedFileTypeException.class,
            SpoofedFileFormatException.class
    })
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            FileValidationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage(), request);
    }

    @ExceptionHandler({
            FileTooLargeException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(Exception exception, HttpServletRequest request) {
        String message = exception instanceof FileTooLargeException
                ? exception.getMessage()
                : "Uploaded file exceeds the allowed size";
        return error(HttpStatus.CONTENT_TOO_LARGE, message, request);
    }

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(FileValidationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler({FileStorageException.class, FileContentAccessException.class})
    public ResponseEntity<ApiErrorResponse> handleFileInfrastructure(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "File infrastructure failure while handling {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    @ExceptionHandler(ConversionJobNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ConversionJobNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ConversionResultNotReadyException.class)
    public ResponseEntity<ApiErrorResponse> handleResultNotReady(
            ConversionResultNotReadyException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(ConversionResultExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleResultExpired(
            ConversionResultExpiredException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.GONE, exception.getMessage(), request);
    }

    @ExceptionHandler(ConversionResultUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleResultUnavailable(
            ConversionResultUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getErrorCode(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected failure while handling {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return error(status, null, message, request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(
                        Instant.now(clock),
                        status.value(),
                        reasonPhrase(status),
                        code,
                        message,
                        request.getRequestURI()
                ));
    }

    private String reasonPhrase(HttpStatus status) {
        return status.getReasonPhrase();
    }

    private String badRequestMessage(Exception exception) {
        if (exception instanceof EmptyFileException
                || exception instanceof InvalidUploadRequestException) {
            return exception.getMessage();
        }
        if (exception instanceof MissingServletRequestPartException) {
            return "Required multipart field is missing";
        }
        if (exception instanceof MissingServletRequestParameterException) {
            return "Required request parameter is missing";
        }
        if (exception instanceof MethodArgumentTypeMismatchException) {
            return "Invalid request parameter";
        }
        return "Malformed multipart request";
    }
}
