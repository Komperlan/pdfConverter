package com.docconverter.application.validation;

import com.docconverter.application.storage.FileContentSource;
import com.docconverter.domain.exception.EmptyFileException;
import com.docconverter.domain.exception.FileContentAccessException;
import com.docconverter.domain.exception.FileTooLargeException;
import com.docconverter.domain.exception.InvalidUploadRequestException;
import com.docconverter.domain.exception.SpoofedFileFormatException;
import com.docconverter.domain.exception.UnsupportedFileTypeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class FileValidator {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_FILENAME_LENGTH = 255;

    private static final String DOC_EXTENSION = "doc";
    private static final String DOCX_EXTENSION = "docx";
    private static final String DOC_MIME_TYPE = "application/msword";
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String OCTET_STREAM_MIME_TYPE = "application/octet-stream";
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Map<String, Set<String>> SUPPORTED_MIME_TYPES = Map.of(
            DOC_EXTENSION, Set.of(DOC_MIME_TYPE, "application/x-tika-msoffice"),
            DOCX_EXTENSION, Set.of(DOCX_MIME_TYPE, "application/x-tika-ooxml")
    );

    private final Tika tika;

    public FileValidator() {
        this(new Tika());
    }

    FileValidator(Tika tika) {
        this.tika = Objects.requireNonNull(tika, "tika must not be null");
    }

    public ValidatedFile validate(ValidateFileCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        validateContentSize(command.sizeBytes());

        String baseFilename = extractBaseFilename(command.originalFilename());
        String fileExtension = extractFileExtension(baseFilename);
        validateSupportedExtension(fileExtension);

        String declaredMimeType = normalizeMimeType(command.declaredMimeType());
        validateDeclaredMimeType(fileExtension, declaredMimeType);

        String detectedMimeType = detectMimeType(command.contentSource());
        validateDetectedMimeType(fileExtension, detectedMimeType);

        return new ValidatedFile(
                baseFilename,
                sanitizeFilename(baseFilename, fileExtension),
                fileExtension,
                declaredMimeType,
                detectedMimeType,
                command.sizeBytes(),
                command.contentSource()
        );
    }

    private void validateContentSize(long sizeBytes) {
        if (sizeBytes == 0) {
            throw new EmptyFileException();
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new FileTooLargeException(sizeBytes, MAX_FILE_SIZE_BYTES);
        }
    }

    private String detectMimeType(FileContentSource contentSource) {
        try (InputStream content = contentSource.openStream()) {
            return normalizeMimeType(tika.detect(content));
        } catch (IOException exc) {
            throw new FileContentAccessException("Failed to inspect uploaded file", exc);
        }
    }

    private String extractBaseFilename(String originalFilename) {
        String normalized = originalFilename.strip().replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        String baseFilename = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        if (baseFilename.isBlank()) {
            throw new UnsupportedFileTypeException("Filename must contain a file name");
        }
        if (baseFilename.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidUploadRequestException("Filename must not exceed 255 characters");
        }
        return baseFilename;
    }

    private String extractFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            throw new UnsupportedFileTypeException("File must have .doc or .docx extension");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateSupportedExtension(String fileExtension) {
        if (!SUPPORTED_MIME_TYPES.containsKey(fileExtension)) {
            throw new UnsupportedFileTypeException("Unsupported file extension: " + fileExtension);
        }
    }

    private void validateDeclaredMimeType(String fileExtension, String declaredMimeType) {
        if (declaredMimeType == null || OCTET_STREAM_MIME_TYPE.equals(declaredMimeType)) {
            return;
        }
        if (!isMimeTypeCompatible(fileExtension, declaredMimeType)) {
            throw new UnsupportedFileTypeException(
                    "Declared MIME type does not match file extension: " + declaredMimeType);
        }
    }

    private void validateDetectedMimeType(String fileExtension, String detectedMimeType) {
        if (!isMimeTypeCompatible(fileExtension, detectedMimeType)) {
            throw new SpoofedFileFormatException(fileExtension, detectedMimeType);
        }
    }

    private boolean isMimeTypeCompatible(String fileExtension, String mimeType) {
        return SUPPORTED_MIME_TYPES
                .getOrDefault(fileExtension, Set.of())
                .contains(mimeType);
    }

    private String sanitizeFilename(String filename, String fileExtension) {
        String sanitized = CONTROL_CHARS.matcher(filename.strip()).replaceAll("_");
        sanitized = UNSAFE_FILENAME_CHARS.matcher(sanitized).replaceAll("_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^\\.+", "");

        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "document." + fileExtension;
        }

        if (!sanitized.toLowerCase(Locale.ROOT).endsWith("." + fileExtension)) {
            return sanitized + "." + fileExtension;
        }
        return sanitized;
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        String normalized = mimeType.strip().toLowerCase(Locale.ROOT);
        int parametersIndex = normalized.indexOf(';');
        return parametersIndex < 0
                ? normalized
                : normalized.substring(0, parametersIndex).strip();
    }
}
