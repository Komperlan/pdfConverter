package com.docconverter.infrastructure.carbone;

import static com.docconverter.infrastructure.config.ConfigurationValues.requirePositive;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "doc-converter.carbone")
public class CarboneProperties {

    private URI baseUrl = URI.create("http://localhost:4000");
    private String apiToken;
    private String apiVersion;
    private String converter = "L";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(65);
    private DataSize maxResponseSize = DataSize.ofMegabytes(50);

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = requireHttpBaseUrl(baseUrl);
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = normalizeOptionalText(apiToken);
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = normalizeOptionalText(apiVersion);
    }

    public String getConverter() {
        return converter;
    }

    public void setConverter(String converter) {
        if (converter == null || converter.isBlank()) {
            throw new IllegalArgumentException("Carbone converter must not be blank");
        }
        String normalized = converter.strip().toUpperCase(Locale.ROOT);
        if (!"L".equals(normalized) && !"O".equals(normalized)) {
            throw new IllegalArgumentException("Carbone converter must be L or O");
        }
        this.converter = normalized;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = requirePositive(readTimeout, "readTimeout");
    }

    public DataSize getMaxResponseSize() {
        return maxResponseSize;
    }

    public void setMaxResponseSize(DataSize maxResponseSize) {
        if (maxResponseSize == null || maxResponseSize.toBytes() <= 0) {
            throw new IllegalArgumentException("maxResponseSize must be positive");
        }
        if (maxResponseSize.toBytes() >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxResponseSize must be less than 2 GB");
        }
        this.maxResponseSize = maxResponseSize;
    }

    public long getMaxResponseSizeBytes() {
        return maxResponseSize.toBytes();
    }

    private static URI requireHttpBaseUrl(URI value) {
        if (value == null || value.getScheme() == null || value.getHost() == null) {
            throw new IllegalArgumentException("Carbone baseUrl must be an absolute HTTP URL");
        }
        String scheme = value.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Carbone baseUrl must use HTTP or HTTPS");
        }
        if (value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("Carbone baseUrl must not contain query or fragment");
        }
        String path = value.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalArgumentException("Carbone baseUrl must not contain a path");
        }
        return value;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
