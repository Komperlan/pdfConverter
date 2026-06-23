package com.docconverter.infrastructure.carbone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docconverter.application.conversion.ConvertDocumentCommand;
import com.docconverter.application.conversion.DocumentConversionException;
import com.docconverter.application.conversion.DocumentConversionFailureType;
import com.docconverter.application.storage.FileContentSource;
import com.docconverter.infrastructure.config.CarboneConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class CarboneDocumentConverterIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\nintegration-result"
            .getBytes(StandardCharsets.US_ASCII);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();

    private HttpServer server;
    private CarboneProperties properties;
    private CarboneDocumentConverterAdapter adapter;
    private int responseStatus;
    private byte[] responseBody;
    private String responseRequestId;
    private Duration responseDelay;
    private String requestMethod;
    private String requestPath;
    private String requestQuery;
    private String authorizationHeader;
    private String versionHeader;
    private String acceptHeader;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        responseBody = PDF;
        responseRequestId = "carbone-request-42";
        responseDelay = Duration.ZERO;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/render/template", this::handleRequest);
        server.start();

        properties = new CarboneProperties();
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setApiToken("secret-token");
        properties.setApiVersion("5");
        adapter = new CarboneDocumentConverterAdapter(
                new CarboneConfiguration().carboneRestClient(properties),
                properties
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsInlineRenderRequestAndReturnsPdf() throws Exception {
        byte[] source = "DOCX-content".getBytes(StandardCharsets.UTF_8);
        UUID jobId = UUID.randomUUID();

        var result = adapter.convert(command(jobId, source));

        assertServerSucceeded();
        assertThat(requestMethod).isEqualTo("POST");
        assertThat(requestPath).isEqualTo("/render/template");
        assertThat(requestQuery).isEqualTo("download=true");
        assertThat(authorizationHeader).isEqualTo("Bearer secret-token");
        assertThat(versionHeader).isEqualTo("5");
        assertThat(acceptHeader).contains("application/pdf");
        assertThat(requestBody.get().path("template").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(source));
        assertThat(requestBody.get().path("convertTo").asText()).isEqualTo("pdf");
        assertThat(requestBody.get().path("converter").asText()).isEqualTo("L");
        assertThat(requestBody.get().path("reportName").asText())
                .isEqualTo(jobId + ".pdf");
        assertThat(result.externalRequestId()).isEqualTo("carbone-request-42");
        assertThat(result.mediaType()).isEqualTo("application/pdf");
        try (var input = result.contentSource().openStream()) {
            assertThat(input.readAllBytes()).isEqualTo(PDF);
        }
    }

    @Test
    void mapsServiceUnavailableToRetryableFailure() {
        responseStatus = 503;
        responseBody = "unavailable".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.convert(command(UUID.randomUUID(), new byte[]{1, 2, 3})))
                .isInstanceOfSatisfying(
                        DocumentConversionException.class,
                        failure -> {
                            assertThat(failure.getFailureType())
                                    .isEqualTo(DocumentConversionFailureType.CONVERTER_UNAVAILABLE);
                            assertThat(failure.isRetryable()).isTrue();
                            assertThat(failure.getExternalRequestId())
                                    .isEqualTo("carbone-request-42");
                        }
                );
        assertServerSucceeded();
    }

    @Test
    void mapsRejectedDocumentToNonRetryableFailure() {
        responseStatus = 415;
        responseBody = "unsupported document".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.convert(command(UUID.randomUUID(), new byte[]{1, 2, 3})))
                .isInstanceOfSatisfying(
                        DocumentConversionException.class,
                        failure -> {
                            assertThat(failure.getFailureType())
                                    .isEqualTo(DocumentConversionFailureType.INVALID_SOURCE_DOCUMENT);
                            assertThat(failure.isRetryable()).isFalse();
                            assertThat(failure.getExternalRequestId())
                                    .isEqualTo("carbone-request-42");
                        }
                );
        assertServerSucceeded();
    }

    @Test
    void mapsRealHttpReadTimeoutToRetryableFailure() {
        responseDelay = Duration.ofMillis(300);
        properties.setReadTimeout(Duration.ofMillis(100));
        adapter = new CarboneDocumentConverterAdapter(
                new CarboneConfiguration().carboneRestClient(properties),
                properties
        );

        assertThatThrownBy(() -> adapter.convert(command(UUID.randomUUID(), new byte[]{1, 2, 3})))
                .isInstanceOfSatisfying(
                        DocumentConversionException.class,
                        failure -> {
                            assertThat(failure.getFailureType())
                                    .isEqualTo(DocumentConversionFailureType.CONVERTER_TIMEOUT);
                            assertThat(failure.isRetryable()).isTrue();
                        }
                );
    }

    @Test
    void rejectsSuccessfulNonPdfResponse() {
        responseBody = "not-a-pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.convert(command(UUID.randomUUID(), new byte[]{1, 2, 3})))
                .isInstanceOfSatisfying(
                        DocumentConversionException.class,
                        failure -> assertThat(failure.getFailureType())
                                .isEqualTo(DocumentConversionFailureType.INVALID_CONVERTER_RESPONSE)
                );
        assertServerSucceeded();
    }

    @Test
    void rejectsResponseLargerThanConfiguredLimit() {
        properties.setMaxResponseSize(DataSize.ofBytes(8));
        adapter = new CarboneDocumentConverterAdapter(
                new CarboneConfiguration().carboneRestClient(properties),
                properties
        );

        assertThatThrownBy(() -> adapter.convert(command(UUID.randomUUID(), new byte[]{1, 2, 3})))
                .isInstanceOfSatisfying(
                        DocumentConversionException.class,
                        failure -> {
                            assertThat(failure.getFailureType())
                                    .isEqualTo(DocumentConversionFailureType.INVALID_CONVERTER_RESPONSE);
                            assertThat(failure.getExternalRequestId())
                                    .isEqualTo("carbone-request-42");
                        }
                );
        assertServerSucceeded();
    }

    @Test
    void mapsConnectionRefusalToRetryableFailure() {
        server.stop(0);
        server = null;

        assertThatThrownBy(() -> adapter.convert(command(UUID.randomUUID(), new byte[]{1, 2, 3})))
                .isInstanceOfSatisfying(
                        DocumentConversionException.class,
                        failure -> {
                            assertThat(failure.getFailureType())
                                    .isEqualTo(DocumentConversionFailureType.CONVERTER_UNAVAILABLE);
                            assertThat(failure.isRetryable()).isTrue();
                        }
                );
    }

    private ConvertDocumentCommand command(UUID jobId, byte[] content) {
        return new ConvertDocumentCommand(
                jobId,
                1,
                "document.docx",
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content.length,
                FileContentSource.fromBytes(content)
        );
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            requestMethod = exchange.getRequestMethod();
            requestPath = exchange.getRequestURI().getPath();
            requestQuery = exchange.getRequestURI().getQuery();
            authorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            versionHeader = exchange.getRequestHeaders().getFirst("carbone-version");
            acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            if (!responseDelay.isZero()) {
                Thread.sleep(responseDelay);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.getResponseHeaders().add("Carbone-Request-Id", responseRequestId);
            exchange.sendResponseHeaders(responseStatus, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            serverFailure.compareAndSet(null, failure);
        }
    }

    private void assertServerSucceeded() {
        assertThat(serverFailure.get()).isNull();
    }
}
