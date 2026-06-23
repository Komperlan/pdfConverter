package com.docconverter.api.controllers;

import com.docconverter.api.dto.ApiErrorResponse;
import com.docconverter.application.port.in.GetConversionResultUseCase;
import com.docconverter.application.result.ConversionResult;
import com.docconverter.domain.exception.FileContentAccessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/conversions")
@RequiredArgsConstructor
@Tag(name = "Conversions", description = "DOC/DOCX to PDF conversion jobs")
public class ConversionResultController {

    private final GetConversionResultUseCase getConversionResultUseCase;

    @Operation(summary = "Download conversion result", description = "Streams the generated PDF")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Generated PDF",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Conversion job not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conversion is not completed yet",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "410",
                    description = "Conversion result has expired",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Conversion failed and has no result",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Result storage error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping(value = "/{id}/result", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<StreamingResponseBody> download(
            @Parameter(description = "Conversion job ID", required = true)
            @PathVariable("id") UUID id
    ) {
        ConversionResult result = getConversionResultUseCase.getResult(id);
        InputStream resultStream = openResultStream(result);
        StreamingResponseBody responseBody = output -> {
            try (resultStream) {
                resultStream.transferTo(output);
            }
        };

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(result.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(responseBody);
    }

    private InputStream openResultStream(ConversionResult result) {
        try {
            return result.contentSource().openStream();
        } catch (IOException exc) {
            throw new FileContentAccessException("Failed to open conversion result", exc);
        }
    }
}
