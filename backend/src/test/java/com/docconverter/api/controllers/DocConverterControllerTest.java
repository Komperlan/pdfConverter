package com.docconverter.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docconverter.api.errors.ApiExceptionHandler;
import com.docconverter.application.port.in.GetConversionJobUseCase;
import com.docconverter.application.port.in.SubmitConversionCommand;
import com.docconverter.application.port.in.SubmitConversionUseCase;
import com.docconverter.domain.exception.ConversionJobNotFoundException;
import com.docconverter.domain.exception.EmptyFileException;
import com.docconverter.domain.exception.UnsupportedFileTypeException;
import com.docconverter.domain.model.ConversionAttempt;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.testsupport.TestDocuments;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocConverterControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-22T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SubmitConversionUseCase submitConversionUseCase;
    private GetConversionJobUseCase getConversionJobUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        submitConversionUseCase = mock(SubmitConversionUseCase.class);
        getConversionJobUseCase = mock(GetConversionJobUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DocConverterController(
                        submitConversionUseCase,
                        getConversionJobUseCase,
                        CLOCK
                ))
                .setControllerAdvice(new ApiExceptionHandler(CLOCK))
                .build();
    }

    @Test
    void createReturnsAcceptedJob() throws Exception {
        Instant expiresAt = NOW.plusSeconds(86_400);
        ConversionJob job = ConversionJob.create(
                "report.docx",
                "report.docx",
                "source/2026/06/20/file.docx",
                "docx",
                TestDocuments.DOCX_MIME_TYPE,
                "application/x-tika-ooxml",
                100,
                "1".repeat(64),
                NOW,
                expiresAt,
                3
        );
        UUID id = job.getId();
        when(submitConversionUseCase.submit(any(SubmitConversionCommand.class))).thenReturn(job);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                TestDocuments.DOCX_MIME_TYPE,
                TestDocuments.minimalDocx()
        );

        mockMvc.perform(multipart("/api/v1/conversions").file(file))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/conversions/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.originalFilename").value("report.docx"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.fileSizeBytes").value(100))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.createdAt").value(job.getCreatedAt().toString()))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.resultAvailable").value(false));

        ArgumentCaptor<SubmitConversionCommand> command = ArgumentCaptor.forClass(SubmitConversionCommand.class);
        verify(submitConversionUseCase).submit(command.capture());
        assertThat(command.getValue().originalFilename()).isEqualTo("report.docx");
        assertThat(command.getValue().declaredMimeType()).isEqualTo(TestDocuments.DOCX_MIME_TYPE);
    }

    @Test
    void createReturnsBadRequestForEmptyFile() throws Exception {
        when(submitConversionUseCase.submit(any(SubmitConversionCommand.class)))
                .thenThrow(new EmptyFileException());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.docx",
                TestDocuments.DOCX_MIME_TYPE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/conversions").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Uploaded file must not be empty"))
                .andExpect(jsonPath("$.path").value("/api/v1/conversions"));
    }

    @Test
    void createReturnsUnsupportedMediaTypeForUnsupportedFile() throws Exception {
        when(submitConversionUseCase.submit(any(SubmitConversionCommand.class)))
                .thenThrow(new UnsupportedFileTypeException("Unsupported file extension: pdf"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                "%PDF".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/conversions").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message", startsWith("Unsupported file extension")))
                .andExpect(jsonPath("$.path").value("/api/v1/conversions"));
    }

    @Test
    void getByIdReturnsCurrentJobStatusAndMetadata() throws Exception {
        Instant expiresAt = NOW.plusSeconds(86_400);
        ConversionJob job = ConversionJob.create(
                "report.docx",
                "report.docx",
                "source/2026/06/20/file.docx",
                "docx",
                TestDocuments.DOCX_MIME_TYPE,
                "application/x-tika-ooxml",
                100,
                "1".repeat(64),
                NOW,
                expiresAt,
                3
        );
        Instant startedAt = job.getCreatedAt();
        Instant finishedAt = startedAt.plusSeconds(20);
        job.startProcessing(startedAt);
        job.registerAttempt(ConversionAttempt.start(1, startedAt));
        job.complete("result/2026/06/20/file.pdf", finishedAt);
        UUID id = job.getId();
        when(getConversionJobUseCase.getById(id)).thenReturn(job);

        mockMvc.perform(get("/api/v1/conversions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.originalFilename").value("report.docx"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileSizeBytes").value(100))
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.createdAt").value(job.getCreatedAt().toString()))
                .andExpect(jsonPath("$.updatedAt").value(job.getUpdatedAt().toString()))
                .andExpect(jsonPath("$.processingStartedAt").value(startedAt.toString()))
                .andExpect(jsonPath("$.processingFinishedAt").value(finishedAt.toString()))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.resultAvailable").value(true));

        verify(getConversionJobUseCase).getById(id);
    }

    @Test
    void getByIdReturnsNotFoundForMissingJob() throws Exception {
        UUID id = UUID.randomUUID();
        when(getConversionJobUseCase.getById(id))
                .thenThrow(new ConversionJobNotFoundException(id));

        mockMvc.perform(get("/api/v1/conversions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Conversion job not found: " + id))
                .andExpect(jsonPath("$.path").value("/api/v1/conversions/" + id));
    }

    @Test
    void getByIdReturnsBadRequestForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/conversions/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));
    }
}
