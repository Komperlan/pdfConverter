package com.docconverter.api.controllers;

import com.docconverter.api.dto.ApiErrorResponse;
import com.docconverter.api.dto.ConversionJobResponse;
import com.docconverter.application.port.in.GetConversionJobUseCase;
import com.docconverter.application.port.in.SubmitConversionCommand;
import com.docconverter.application.port.in.SubmitConversionUseCase;
import com.docconverter.domain.model.ConversionJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/conversions")
@RequiredArgsConstructor
@Tag(name = "Conversions", description = "DOC/DOCX to PDF conversion jobs")
public class DocConverterController {

    private final SubmitConversionUseCase submitConversionUseCase;
    private final GetConversionJobUseCase getConversionJobUseCase;
    private final Clock clock;

    @Operation(
            summary = "Create a conversion job",
            description = "Uploads one DOC or DOCX file up to 10 MB for asynchronous conversion"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Conversion job created",
                    content = @Content(schema = @Schema(implementation = ConversionJobResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "File is missing or empty",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "File exceeds 10 MB",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "File format is unsupported or spoofed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal storage or persistence error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ConversionJobResponse> create(
            @Parameter(description = "DOC or DOCX document", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        ConversionJob job = submitConversionUseCase.submit(new SubmitConversionCommand(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file::getInputStream
        ));

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/conversions/" + job.getId()))
                .body(ConversionJobResponse.from(job, Instant.now(clock)));
    }

    @Operation(summary = "Get a conversion job", description = "Returns current status and metadata")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Conversion job found",
                    content = @Content(schema = @Schema(implementation = ConversionJobResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Conversion job not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping(
            value = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ConversionJobResponse getById(
            @Parameter(description = "Conversion job ID", required = true)
            @PathVariable("id") UUID id
    ) {
        return ConversionJobResponse.from(getConversionJobUseCase.getById(id), Instant.now(clock));
    }
}
