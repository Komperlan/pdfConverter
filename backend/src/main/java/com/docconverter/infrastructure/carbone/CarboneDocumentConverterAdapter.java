package com.docconverter.infrastructure.carbone;

import com.docconverter.application.conversion.ConvertDocumentCommand;
import com.docconverter.application.conversion.ConvertedDocument;
import com.docconverter.application.conversion.DocumentConversionException;
import com.docconverter.application.conversion.DocumentConversionFailureType;
import com.docconverter.application.port.out.DocumentConverterPort;
import com.docconverter.application.storage.FileContentSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CarboneDocumentConverterAdapter implements DocumentConverterPort {

    private static final String INLINE_CONVERSION_PATH = "/render/template?download=true";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CARBONE_REQUEST_ID_HEADER = "Carbone-Request-Id";
    private static final String RENDER_ID_HEADER = "X-Render-Id";

    private final RestClient restClient;
    private final CarboneProperties properties;

    public CarboneDocumentConverterAdapter(
            @Qualifier("carboneRestClient") RestClient restClient,
            CarboneProperties properties
    ) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public ConvertedDocument convert(ConvertDocumentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        byte[] sourceContent = readSourceContent(command);
        String resultFilename = command.jobId() + ".pdf";
        CarboneRenderRequest request = new CarboneRenderRequest(
                Map.of(),
                Base64.getEncoder().encodeToString(sourceContent),
                "pdf",
                properties.getConverter(),
                resultFilename
        );

        try {
            return restClient.post()
                    .uri(INLINE_CONVERSION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, response) -> handleResponse(
                            response.getStatusCode(),
                            response.getHeaders(),
                            response.getBody(),
                            resultFilename
                    ));
        } catch (DocumentConversionException exc) {
            throw exc;
        } catch (ResourceAccessException exc) {
            throw connectionFailure(exc);
        } catch (RestClientException exc) {
            throw new DocumentConversionException(
                    DocumentConversionFailureType.INTERNAL_ERROR,
                    "Failed to execute Carbone conversion request",
                    exc
            );
        }
    }

    private ConvertedDocument handleResponse(
            HttpStatusCode status,
            HttpHeaders headers,
            InputStream responseBody,
            String resultFilename
    ) throws IOException {
        String externalRequestId = extractExternalRequestId(headers);
        if (!status.is2xxSuccessful()) {
            throw httpFailure(status, externalRequestId);
        }

        long contentLength = headers.getContentLength();
        long maxResponseSize = properties.getMaxResponseSizeBytes();
        if (contentLength == 0) {
            throw invalidResponse("Carbone returned an empty response", externalRequestId);
        }
        if (contentLength > maxResponseSize) {
            throw invalidResponse("Carbone response exceeds the configured size limit", externalRequestId);
        }
        if (responseBody == null) {
            throw invalidResponse("Carbone returned an empty response", externalRequestId);
        }

        byte[] pdfContent = readResponseBody(responseBody, maxResponseSize, externalRequestId);
        validatePdf(pdfContent, externalRequestId);

        return new ConvertedDocument(
                externalRequestId,
                resultFilename,
                ConvertedDocument.PDF_MEDIA_TYPE,
                pdfContent.length,
                FileContentSource.fromBytes(pdfContent)
        );
    }

    private byte[] readSourceContent(ConvertDocumentCommand command) {
        if (command.sourceSizeBytes() >= Integer.MAX_VALUE) {
            throw new DocumentConversionException(
                    DocumentConversionFailureType.SOURCE_CONTENT_UNAVAILABLE,
                    "Source document is too large to send to Carbone"
            );
        }

        int expectedSize = Math.toIntExact(command.sourceSizeBytes());
        try (InputStream input = command.contentSource().openStream()) {
            byte[] content = input.readNBytes(expectedSize + 1);
            if (content.length != expectedSize) {
                throw new DocumentConversionException(
                        DocumentConversionFailureType.SOURCE_CONTENT_UNAVAILABLE,
                        "Source document size does not match its metadata"
                );
            }
            return content;
        } catch (IOException exc) {
            throw new DocumentConversionException(
                    DocumentConversionFailureType.SOURCE_CONTENT_UNAVAILABLE,
                    "Failed to read source document",
                    exc
            );
        }
    }

    private byte[] readResponseBody(
            InputStream responseBody,
            long maxResponseSize,
            String externalRequestId
    ) throws IOException {
        int limit = Math.toIntExact(maxResponseSize);
        byte[] content = responseBody.readNBytes(limit + 1);
        if (content.length > limit) {
            throw invalidResponse("Carbone response exceeds the configured size limit", externalRequestId);
        }
        if (content.length == 0) {
            throw invalidResponse("Carbone returned an empty response", externalRequestId);
        }
        return content;
    }

    private void validatePdf(byte[] content, String externalRequestId) {
        if (content.length < 5
                || content[0] != '%'
                || content[1] != 'P'
                || content[2] != 'D'
                || content[3] != 'F'
                || content[4] != '-') {
            throw invalidResponse("Carbone response is not a PDF document", externalRequestId);
        }
    }

    private DocumentConversionException httpFailure(
            HttpStatusCode status,
            String externalRequestId
    ) {
        int statusCode = status.value();
        if (statusCode == 408 || statusCode == 504) {
            return new DocumentConversionException(
                    DocumentConversionFailureType.CONVERTER_TIMEOUT,
                    "Carbone conversion timed out",
                    externalRequestId,
                    null
            );
        }
        if (statusCode == 413 || statusCode == 415) {
            return new DocumentConversionException(
                    DocumentConversionFailureType.INVALID_SOURCE_DOCUMENT,
                    "Carbone rejected the source document",
                    externalRequestId,
                    null
            );
        }
        if (statusCode == 429 || status.is5xxServerError()) {
            return new DocumentConversionException(
                    DocumentConversionFailureType.CONVERTER_UNAVAILABLE,
                    "Carbone is temporarily unavailable",
                    externalRequestId,
                    null
            );
        }
        return new DocumentConversionException(
                DocumentConversionFailureType.INTERNAL_ERROR,
                "Carbone rejected the conversion request with HTTP " + statusCode,
                externalRequestId,
                null
        );
    }

    private DocumentConversionException connectionFailure(ResourceAccessException cause) {
        if (hasTimeoutCause(cause)) {
            return new DocumentConversionException(
                    DocumentConversionFailureType.CONVERTER_TIMEOUT,
                    "Carbone conversion timed out",
                    cause
            );
        }
        return new DocumentConversionException(
                DocumentConversionFailureType.CONVERTER_UNAVAILABLE,
                "Cannot connect to Carbone",
                cause
        );
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            if (current == current.getCause()) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private DocumentConversionException invalidResponse(String message, String externalRequestId) {
        return new DocumentConversionException(
                DocumentConversionFailureType.INVALID_CONVERTER_RESPONSE,
                message,
                externalRequestId,
                null
        );
    }

    private String extractExternalRequestId(HttpHeaders headers) {
        String requestId = firstNonBlank(
                headers.getFirst(CARBONE_REQUEST_ID_HEADER),
                headers.getFirst(RENDER_ID_HEADER),
                headers.getFirst(REQUEST_ID_HEADER)
        );
        return requestId == null ? null : requestId.strip();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record CarboneRenderRequest(
            Map<String, Object> data,
            String template,
            String convertTo,
            String converter,
            String reportName
    ) {
    }
}
