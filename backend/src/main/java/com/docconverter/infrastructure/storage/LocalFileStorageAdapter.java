package com.docconverter.infrastructure.storage;

import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.application.storage.StoreFileCommand;
import com.docconverter.application.storage.StoredFile;
import com.docconverter.application.storage.StoredFileContent;
import com.docconverter.application.storage.StoredFilePurpose;
import com.docconverter.domain.exception.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private static final DateTimeFormatter YEAR_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd").withZone(ZoneOffset.UTC);
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("[a-z0-9]{1,16}");

    private final LocalFileStorageProperties properties;
    private final Clock clock;

    @Autowired
    public LocalFileStorageAdapter(LocalFileStorageProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LocalFileStorageAdapter(LocalFileStorageProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public StoredFile save(StoreFileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant storedAt = Instant.now(clock);
        String extension = normalizeExtension(command.fileExtension());
        Path relativeDirectory = relativeDirectory(command.purpose(), storedAt);
        String filename = UUID.randomUUID() + "." + extension;
        Path relativePath = relativeDirectory.resolve(filename);
        Path root = ensureRootDirectory();
        Path target = resolveStoragePath(root, toStoragePath(relativePath));
        ensureStorageDirectory(root, target.getParent());

        MessageDigest digest = sha256Digest();
        long actualSize;
        try (InputStream input = command.contentSource().openStream();
             DigestInputStream digestInput = new DigestInputStream(input, digest);
             OutputStream output = Files.newOutputStream(
                     target,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE
             )) {
            actualSize = digestInput.transferTo(output);
        } catch (IOException | RuntimeException exc) {
            deletePartialFile(target, exc);
            throw new FileStorageException("Failed to store file: " + relativePath, exc);
        }

        if (actualSize != command.sizeBytes()) {
            FileStorageException exc = new FileStorageException(
                    "File size changed while storing: expected "
                            + command.sizeBytes()
                            + " bytes, received "
                            + actualSize
            );
            deletePartialFile(target, exc);
            throw exc;
        }

        return new StoredFile(
                command.purpose(),
                toStoragePath(relativePath),
                filename,
                extension,
                command.mediaType(),
                actualSize,
                HexFormat.of().formatHex(digest.digest()),
                storedAt
        );
    }

    @Override
    public StoredFileContent load(String storagePath) {
        safeRelativePath(storagePath);
        Path root = requireRootDirectory();
        Path file = requireRegularStoredFile(root, storagePath);

        long sizeBytes;
        try {
            sizeBytes = Files.size(file);
        } catch (IOException exc) {
            throw new FileStorageException("Failed to read stored file metadata: " + storagePath, exc);
        }

        Path relativePath = root.relativize(file);
        String filename = file.getFileName().toString();
        return new StoredFileContent(
                toStoragePath(relativePath),
                filename,
                mediaTypeFromFilename(filename),
                sizeBytes,
                () -> Files.newInputStream(
                        requireRegularStoredFile(root, storagePath),
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS
                )
        );
    }

    @Override
    public void delete(String storagePath) {
        safeRelativePath(storagePath);
        Path root = requireRootDirectory();
        Path file = resolveStoragePath(root, storagePath);
        validateExistingDirectories(root, file.getParent());
        if (Files.isSymbolicLink(file)) {
            throw new FileStorageException("Symbolic links are not allowed in storage paths");
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exc) {
            throw new FileStorageException("Failed to delete stored file: " + storagePath, exc);
        }
    }

    private Path ensureRootDirectory() {
        Path root = configuredRootDirectory();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (!properties.isCreateDirectories()) {
                throw new FileStorageException("Storage directory does not exist: " + root);
            }
            try {
                Files.createDirectories(root);
            } catch (IOException exc) {
                throw new FileStorageException("Failed to create storage directory", exc);
            }
        }
        return requireDirectory(root, "Storage root path");
    }

    private Path requireRootDirectory() {
        return requireDirectory(configuredRootDirectory(), "Storage root path");
    }

    private Path requireDirectory(Path directory, String description) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileStorageException(description + " does not exist");
        }
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileStorageException(description + " must be a real directory");
        }
        try {
            return directory.toRealPath();
        } catch (IOException exc) {
            throw new FileStorageException("Failed to resolve storage directory", exc);
        }
    }

    private Path configuredRootDirectory() {
        Path rootDirectory = properties.getRootDirectory();
        if (rootDirectory == null) {
            throw new FileStorageException("Storage root directory must be configured");
        }
        return rootDirectory.toAbsolutePath().normalize();
    }

    private void ensureStorageDirectory(Path root, Path directory) {
        requireWithinRoot(root, directory);
        Path current = root;
        for (Path segment : root.relativize(directory)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                requireSafeStorageDirectory(current);
                continue;
            }
            if (!properties.isCreateDirectories()) {
                throw new FileStorageException("Storage directory does not exist");
            }
            try {
                Files.createDirectory(current);
            } catch (FileAlreadyExistsException race) {
                requireSafeStorageDirectory(current);
            } catch (IOException exc) {
                throw new FileStorageException("Failed to create storage directory", exc);
            }
        }
    }

    private void validateExistingDirectories(Path root, Path directory) {
        requireWithinRoot(root, directory);
        Path current = root;
        for (Path segment : root.relativize(directory)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            requireSafeStorageDirectory(current);
        }
    }

    private void requireSafeStorageDirectory(Path directory) {
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileStorageException("Storage path contains an unsafe directory");
        }
    }

    private Path requireRegularStoredFile(Path root, String storagePath) {
        Path file = resolveStoragePath(root, storagePath);
        validateExistingDirectories(root, file.getParent());
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileStorageException("Stored file does not exist: " + storagePath);
        }
        try {
            Path realFile = file.toRealPath();
            requireWithinRoot(root, realFile);
            return realFile;
        } catch (IOException exc) {
            throw new FileStorageException("Failed to resolve stored file", exc);
        }
    }

    private void requireWithinRoot(Path root, Path path) {
        if (!path.normalize().startsWith(root)) {
            throw new FileStorageException("Storage path escapes root directory");
        }
    }

    private Path relativeDirectory(StoredFilePurpose purpose, Instant storedAt) {
        String purposeDirectory = switch (purpose) {
            case SOURCE_DOCUMENT -> properties.getSourceDirectory();
            case RESULT_PDF -> properties.getResultDirectory();
        };
        return safeRelativePath(purposeDirectory)
                .resolve(YEAR_FORMATTER.format(storedAt))
                .resolve(MONTH_FORMATTER.format(storedAt))
                .resolve(DAY_FORMATTER.format(storedAt));
    }

    private Path resolveStoragePath(Path root, String storagePath) {
        Path relativePath = safeRelativePath(storagePath);
        Path resolved = root.resolve(relativePath).normalize();
        requireWithinRoot(root, resolved);
        return resolved;
    }

    private Path safeRelativePath(String value) {
        if (value == null || value.isBlank()) {
            throw new FileStorageException("Storage path must not be blank");
        }
        if (value.contains("\\")) {
            throw new FileStorageException("Storage path must use forward slashes: " + value);
        }

        Path path = Path.of(value);
        if (path.isAbsolute()) {
            throw new FileStorageException("Storage path must be relative: " + value);
        }
        for (Path segment : path) {
            String name = segment.toString();
            if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
                throw new FileStorageException("Unsafe storage path: " + value);
            }
        }
        return path.normalize();
    }

    private String normalizeExtension(String fileExtension) {
        if (fileExtension == null || fileExtension.isBlank()) {
            throw new FileStorageException("File extension must not be blank");
        }

        String extension = fileExtension.strip().toLowerCase(Locale.ROOT);
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (!EXTENSION_PATTERN.matcher(extension).matches()) {
            throw new FileStorageException("Unsafe file extension: " + fileExtension);
        }
        return extension;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exc) {
            throw new FileStorageException("SHA-256 digest is not available", exc);
        }
    }

    private void deletePartialFile(Path target, Throwable originalFailure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private String mediaTypeFromFilename(String filename) {
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lowerFilename.endsWith(".doc")) {
            return "application/msword";
        }
        return "application/octet-stream";
    }

    private String toStoragePath(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }
}
