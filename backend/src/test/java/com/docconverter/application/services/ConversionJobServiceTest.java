package com.docconverter.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.docconverter.application.port.in.SubmitConversionCommand;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.application.storage.StoreFileCommand;
import com.docconverter.application.storage.StoredFile;
import com.docconverter.application.storage.StoredFilePurpose;
import com.docconverter.application.validation.FileValidator;
import com.docconverter.domain.exception.FileStorageException;
import com.docconverter.domain.exception.UnsupportedFileTypeException;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import com.docconverter.infrastructure.config.ConversionJobProperties;
import com.docconverter.testsupport.TestDocuments;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T10:00:00Z");

    @Mock
    private ConversionJobRepository conversionJobRepository;

    @Mock
    private FileStoragePort fileStoragePort;

    private ConversionJobService service;

    @BeforeEach
    void setUp() {
        service = new ConversionJobService(
                conversionJobRepository,
                fileStoragePort,
                new FileValidator(),
                new ConversionJobProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void submitValidDocxStoresFileAndCreatesJob() throws Exception {
        byte[] content = TestDocuments.minimalDocx();
        StoredFile storedFile = new StoredFile(
                StoredFilePurpose.SOURCE_DOCUMENT,
                "source/2026/06/20/file.docx",
                "file.docx",
                "docx",
                "application/x-tika-ooxml",
                content.length,
                "1".repeat(64),
                Instant.parse("2026-06-20T10:15:30Z")
        );
        when(fileStoragePort.save(any(StoreFileCommand.class))).thenReturn(storedFile);
        when(conversionJobRepository.save(any(ConversionJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConversionJob job = service.submit(new SubmitConversionCommand(
                "report.docx",
                TestDocuments.DOCX_MIME_TYPE,
                content
        ));

        ArgumentCaptor<StoreFileCommand> storeCommand = ArgumentCaptor.forClass(StoreFileCommand.class);
        verify(fileStoragePort).save(storeCommand.capture());
        assertThat(storeCommand.getValue().purpose()).isEqualTo(StoredFilePurpose.SOURCE_DOCUMENT);
        assertThat(storeCommand.getValue().safeFilename()).isEqualTo("report.docx");
        assertThat(storeCommand.getValue().fileExtension()).isEqualTo("docx");
        assertThat(storeCommand.getValue().mediaType())
                .isIn(TestDocuments.DOCX_MIME_TYPE, "application/x-tika-ooxml");
        assertThat(storeCommand.getValue().sizeBytes()).isEqualTo(content.length);
        try (var input = storeCommand.getValue().contentSource().openStream()) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }

        ArgumentCaptor<ConversionJob> savedJob = ArgumentCaptor.forClass(ConversionJob.class);
        verify(conversionJobRepository).save(savedJob.capture());
        assertThat(savedJob.getValue().getOriginalFilename()).isEqualTo("report.docx");
        assertThat(savedJob.getValue().getSafeOriginalFilename()).isEqualTo("report.docx");
        assertThat(savedJob.getValue().getSourceFilePath()).isEqualTo("source/2026/06/20/file.docx");
        assertThat(savedJob.getValue().getFileExtension()).isEqualTo("docx");
        assertThat(savedJob.getValue().getChecksumSha256()).isEqualTo("1".repeat(64));
        assertThat(savedJob.getValue().getMaxAttempts()).isEqualTo(3);
        assertThat(savedJob.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(savedJob.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(86_400));

        assertThat(job.getStatus()).isEqualTo(ConversionStatus.CREATED);
        assertThat(job.getId()).isNotNull();
    }

    @Test
    void submitValidationErrorDoesNotStoreFileOrCreateJob() {
        assertThatThrownBy(() -> service.submit(new SubmitConversionCommand(
                "report.pdf",
                "application/pdf",
                "%PDF".getBytes(StandardCharsets.UTF_8)
        )))
                .isInstanceOf(UnsupportedFileTypeException.class);

        verifyNoInteractions(fileStoragePort, conversionJobRepository);
    }

    @Test
    void submitStorageErrorDoesNotCreateJob() {
        when(fileStoragePort.save(any(StoreFileCommand.class)))
                .thenThrow(new FileStorageException("storage failed"));

        assertThatThrownBy(() -> service.submit(new SubmitConversionCommand(
                "report.docx",
                TestDocuments.DOCX_MIME_TYPE,
                TestDocuments.minimalDocx()
        )))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("storage failed");

        verify(conversionJobRepository, never()).save(any(ConversionJob.class));
    }
}
