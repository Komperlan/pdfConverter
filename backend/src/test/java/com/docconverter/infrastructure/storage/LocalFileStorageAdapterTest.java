package com.docconverter.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docconverter.application.storage.StoreFileCommand;
import com.docconverter.application.storage.StoredFile;
import com.docconverter.application.storage.StoredFileContent;
import com.docconverter.application.storage.StoredFilePurpose;
import com.docconverter.domain.exception.FileStorageException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageAdapterTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-20T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void saveCreatesFileAndReturnsMetadata() throws Exception {
        Path root = tempDir.resolve("storage");
        LocalFileStorageAdapter adapter = adapter(root, true);
        byte[] content = "hello document".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = adapter.save(sourceCommand(content));

        assertThat(stored.purpose()).isEqualTo(StoredFilePurpose.SOURCE_DOCUMENT);
        assertThat(stored.storagePath()).startsWith("source/2026/06/20/");
        assertThat(stored.storagePath()).endsWith(".docx");
        assertThat(stored.filename()).endsWith(".docx");
        assertThat(stored.fileExtension()).isEqualTo("docx");
        assertThat(stored.mediaType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.checksumSha256()).isEqualTo(sha256(content));
        assertThat(stored.storedAt()).isEqualTo(Instant.parse("2026-06-20T10:15:30Z"));
        assertThat(root.resolve(stored.storagePath())).exists().hasBinaryContent(content);
    }

    @Test
    void loadReturnsStoredBytes() throws Exception {
        LocalFileStorageAdapter adapter = adapter(tempDir.resolve("storage"), true);
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);
        StoredFile stored = adapter.save(sourceCommand(content));

        StoredFileContent loaded = adapter.load(stored.storagePath());

        assertThat(loaded.storagePath()).isEqualTo(stored.storagePath());
        assertThat(loaded.filename()).isEqualTo(stored.filename());
        assertThat(loaded.mediaType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(loaded.sizeBytes()).isEqualTo(content.length);
        try (var input = loaded.openStream()) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void deleteRemovesStoredFileAndIsIdempotent() {
        Path root = tempDir.resolve("storage");
        LocalFileStorageAdapter adapter = adapter(root, true);
        StoredFile stored = adapter.save(sourceCommand("data".getBytes(StandardCharsets.UTF_8)));
        Path storedPath = root.resolve(stored.storagePath());

        adapter.delete(stored.storagePath());
        adapter.delete(stored.storagePath());

        assertThat(storedPath).doesNotExist();
    }

    @Test
    void sourceAndResultFilesUseDifferentDirectories() {
        LocalFileStorageAdapter adapter = adapter(tempDir.resolve("storage"), true);

        StoredFile source = adapter.save(sourceCommand("source".getBytes(StandardCharsets.UTF_8)));
        StoredFile result = adapter.save(resultCommand("pdf".getBytes(StandardCharsets.UTF_8)));

        assertThat(source.storagePath()).startsWith("source/");
        assertThat(result.storagePath()).startsWith("result/");
        assertThat(result.storagePath()).endsWith(".pdf");
        assertThat(result.mediaType()).isEqualTo("application/pdf");
    }

    @Test
    void loadRejectsPathTraversal() {
        LocalFileStorageAdapter adapter = adapter(tempDir.resolve("storage"), true);

        assertThatThrownBy(() -> adapter.load("../evil.docx"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Unsafe storage path");
    }

    @Test
    void loadRejectsAbsolutePath() {
        LocalFileStorageAdapter adapter = adapter(tempDir.resolve("storage"), true);

        assertThatThrownBy(() -> adapter.load(tempDir.resolve("evil.docx").toString()))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("relative");
    }

    @Test
    void loadRejectsSymbolicLinkInsideStorageRoot() throws Exception {
        Path root = tempDir.resolve("storage");
        Path sourceDirectory = Files.createDirectories(root.resolve("source"));
        Path outsideFile = tempDir.resolve("outside.docx");
        Files.writeString(outsideFile, "outside");
        Files.createSymbolicLink(sourceDirectory.resolve("link.docx"), outsideFile);
        LocalFileStorageAdapter adapter = adapter(root, true);

        assertThatThrownBy(() -> adapter.load("source/link.docx"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    void saveRejectsSymbolicLinkedStorageDirectory() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("storage"));
        Path outsideDirectory = Files.createDirectories(tempDir.resolve("outside"));
        Files.createSymbolicLink(root.resolve("source"), outsideDirectory);
        LocalFileStorageAdapter adapter = adapter(root, true);

        assertThatThrownBy(() -> adapter.save(sourceCommand("data".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("unsafe directory");
        assertThat(outsideDirectory).isEmptyDirectory();
    }

    @Test
    void saveRejectsUnsafeExtension() {
        LocalFileStorageAdapter adapter = adapter(tempDir.resolve("storage"), true);
        StoreFileCommand command = new StoreFileCommand(
                StoredFilePurpose.SOURCE_DOCUMENT,
                "document.docx",
                "../docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "data".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> adapter.save(command))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Unsafe file extension");
    }

    @Test
    void saveCreatesMissingDirectoriesWhenEnabled() {
        Path root = tempDir.resolve("missing-storage");
        LocalFileStorageAdapter adapter = adapter(root, true);

        StoredFile stored = adapter.save(sourceCommand("data".getBytes(StandardCharsets.UTF_8)));

        assertThat(root).isDirectory();
        assertThat(root.resolve(stored.storagePath())).exists();
    }

    @Test
    void saveFailsWhenRootDirectoryIsMissingAndCreationIsDisabled() {
        Path root = tempDir.resolve("missing-storage");
        LocalFileStorageAdapter adapter = adapter(root, false);

        assertThatThrownBy(() -> adapter.save(sourceCommand("data".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("does not exist");
    }

    private LocalFileStorageAdapter adapter(Path root, boolean createDirectories) {
        LocalFileStorageProperties properties = new LocalFileStorageProperties();
        properties.setRootDirectory(root);
        properties.setCreateDirectories(createDirectories);
        return new LocalFileStorageAdapter(properties, FIXED_CLOCK);
    }

    private StoreFileCommand sourceCommand(byte[] content) {
        return new StoreFileCommand(
                StoredFilePurpose.SOURCE_DOCUMENT,
                "document.docx",
                ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content
        );
    }

    private StoreFileCommand resultCommand(byte[] content) {
        return new StoreFileCommand(
                StoredFilePurpose.RESULT_PDF,
                "document.pdf",
                "pdf",
                "application/pdf",
                content
        );
    }

    private String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
