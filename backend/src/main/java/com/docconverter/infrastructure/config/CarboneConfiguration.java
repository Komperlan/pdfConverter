package com.docconverter.infrastructure.config;

import com.docconverter.infrastructure.carbone.CarboneProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CarboneConfiguration {

    @Bean
    @Qualifier("carboneRestClient")
    public RestClient carboneRestClient(CarboneProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_PDF_VALUE);

        if (properties.getApiToken() != null) {
            builder.defaultHeaders(headers -> headers.setBearerAuth(properties.getApiToken()));
        }
        if (properties.getApiVersion() != null) {
            builder.defaultHeader("carbone-version", properties.getApiVersion());
        }

        return builder.build();
    }
}
