package com.docconverter.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docconverter.domain.exception.EmptyFileException;
import com.docconverter.domain.exception.FileTooLargeException;
import com.docconverter.domain.exception.SpoofedFileFormatException;
import com.docconverter.domain.exception.UnsupportedFileTypeException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    @Test
    void validateAcceptsDocxAndReturnsMetadata() throws Exception {
        byte[] content = minimalDocx();

        ValidatedFile file = validator.validate(new ValidateFileCommand(
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content
        ));

        assertThat(file.originalFilename()).isEqualTo("report.docx");
        assertThat(file.safeFilename()).isEqualTo("report.docx");
        assertThat(file.fileExtension()).isEqualTo("docx");
        assertThat(file.declaredMimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(file.detectedMimeType())
                .isIn(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/x-tika-ooxml"
                );
        assertThat(file.sizeBytes()).isEqualTo(content.length);
        try (var input = file.contentSource().openStream()) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void validateSanitizesFilename() throws Exception {
        ValidatedFile file = validator.validate(new ValidateFileCommand(
                "../user docs/report final.docx",
                null,
                minimalDocx()
        ));

        assertThat(file.safeFilename()).isEqualTo("report_final.docx");
    }

    @Test
    void validateAcceptsOctetStreamDeclaredMimeType() throws Exception {
        ValidatedFile file = validator.validate(new ValidateFileCommand(
                "report.docx",
                "application/octet-stream",
                minimalDocx()
        ));

        assertThat(file.declaredMimeType()).isEqualTo("application/octet-stream");
    }

    @Test
    void validateAcceptsDeclaredMimeTypeWithParameters() throws Exception {
        ValidatedFile file = validator.validate(new ValidateFileCommand(
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document; charset=UTF-8",
                minimalDocx()
        ));

        assertThat(file.declaredMimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void validateRejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validate(new ValidateFileCommand(
                "empty.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]
        )))
                .isInstanceOf(EmptyFileException.class);
    }

    @Test
    void validateRejectsTooLargeFile() {
        byte[] content = new byte[(int) FileValidator.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> validator.validate(new ValidateFileCommand(
                "large.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content
        )))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void validateRejectsUnsupportedExtension() {
        assertThatThrownBy(() -> validator.validate(new ValidateFileCommand(
                "report.pdf",
                "application/pdf",
                "%PDF".getBytes(StandardCharsets.UTF_8)
        )))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("Unsupported file extension");
    }

    @Test
    void validateRejectsWrongDeclaredMimeType() throws Exception {
        assertThatThrownBy(() -> validator.validate(new ValidateFileCommand(
                "report.docx",
                "image/png",
                minimalDocx()
        )))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("Declared MIME type");
    }

    @Test
    void validateRejectsSpoofedContent() {
        assertThatThrownBy(() -> validator.validate(new ValidateFileCommand(
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "plain text".getBytes(StandardCharsets.UTF_8)
        )))
                .isInstanceOf(SpoofedFileFormatException.class)
                .hasMessageContaining("does not match .docx");
    }

    @Test
    void commandAndResultProvideRepeatableContent() throws Exception {
        byte[] content = minimalDocx();
        ValidateFileCommand command = new ValidateFileCommand("report.docx", null, content);
        content[0] = 0;

        ValidatedFile file = validator.validate(command);
        try (var commandInput = command.contentSource().openStream();
             var validatedInput = file.contentSource().openStream()) {
            assertThat(commandInput.readAllBytes()).isNotEqualTo(content);
            assertThat(validatedInput.readAllBytes()).isNotEqualTo(content);
        }
    }

    private byte[] minimalDocx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            addEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1"
                                      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                                      Target="word/document.xml"/>
                    </Relationships>
                    """);
            addEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                        <w:body><w:p><w:r><w:t>DocConverter</w:t></w:r></w:p></w:body>
                    </w:document>
                    """);
        }
        return output.toByteArray();
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
