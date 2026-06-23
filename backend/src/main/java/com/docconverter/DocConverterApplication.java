package com.docconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.docconverter.infrastructure")
public class DocConverterApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocConverterApplication.class, args);
    }
}
