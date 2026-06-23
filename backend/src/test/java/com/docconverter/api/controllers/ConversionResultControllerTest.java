package com.docconverter.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docconverter.api.errors.ApiExceptionHandler;
import com.docconverter.application.port.in.GetConversionResultUseCase;
import com.docconverter.application.result.ConversionResult;
import com.docconverter.application.storage.FileContentSource;
import com.docconverter.domain.exception.ConversionJobNotFoundException;
import com.docconverter.domain.exception.ConversionResultExpiredException;
import com.docconverter.domain.exception.ConversionResultNotReadyException;
import com.docconverter.domain.exception.ConversionResultUnavailableException;
import com.docconverter.domain.model.ConversionErrorCode;
import com.docconverter.domain.model.ConversionStatus;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConversionResultControllerTest {

    private GetConversionResultUseCase getConversionResultUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        getConversionResultUseCase = mock(GetConversionResultUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConversionResultController(getConversionResultUseCase))
                .setControllerAdvice(new ApiExceptionHandler(Clock.systemUTC()))
                .build();
    }

    @Test
    void returnsNotFoundForUnknownJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(getConversionResultUseCase.getResult(jobId))
                .thenThrow(new ConversionJobNotFoundException(jobId));

        mockMvc.perform(get("/api/v1/conversions/{id}/result", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.path").value(
                        "/api/v1/conversions/" + jobId + "/result"
                ));
    }

    @ParameterizedTest
    @EnumSource(value = ConversionStatus.class, names = {"CREATED", "PROCESSING"})
    void returnsConflictWhileResultIsNotReady(ConversionStatus statusValue) throws Exception {
        UUID jobId = UUID.randomUUID();
        when(getConversionResultUseCase.getResult(jobId))
                .thenThrow(new ConversionResultNotReadyException(jobId, statusValue));

        mockMvc.perform(get("/api/v1/conversions/{id}/result", jobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Conversion result is not ready for job "
                                + jobId
                                + ", current status: "
                                + statusValue
                ));
    }

    @Test
    void returnsGoneForExpiredResult() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(getConversionResultUseCase.getResult(jobId))
                .thenThrow(new ConversionResultExpiredException(jobId));

        mockMvc.perform(get("/api/v1/conversions/{id}/result", jobId))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Gone"));
    }

    @Test
    void returnsUnprocessableContentWithSafeFailureDetails() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(getConversionResultUseCase.getResult(jobId))
                .thenThrow(new ConversionResultUnavailableException(
                        jobId,
                        ConversionErrorCode.CONVERTER_TIMEOUT,
                        "Document conversion timed out"
                ));

        mockMvc.perform(get("/api/v1/conversions/{id}/result", jobId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Content"))
                .andExpect(jsonPath("$.code").value("CONVERTER_TIMEOUT"))
                .andExpect(jsonPath("$.message").value(
                        "Conversion failed for job "
                                + jobId
                                + ": Document conversion timed out"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/v1/conversions/" + jobId + "/result"
                ));
    }

    @Test
    void hidesInternalStateError() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(getConversionResultUseCase.getResult(jobId))
                .thenThrow(new IllegalStateException(
                        "Completed conversion job has no result file path"
                ));

        mockMvc.perform(get("/api/v1/conversions/{id}/result", jobId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void streamsCompletedPdf() throws Exception {
        UUID jobId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7\nresult".getBytes(StandardCharsets.US_ASCII);
        AtomicBoolean streamOpened = new AtomicBoolean();
        when(getConversionResultUseCase.getResult(jobId)).thenReturn(new ConversionResult(
                "report.pdf",
                "application/pdf",
                pdf.length,
                () -> {
                    streamOpened.set(true);
                    return new ByteArrayInputStream(pdf);
                }
        ));

        MvcResult initialResult = mockMvc
                .perform(get("/api/v1/conversions/{id}/result", jobId))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(streamOpened).isTrue();

        mockMvc.perform(asyncDispatch(initialResult))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.length)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"report.pdf\"")
                ));
    }
}
