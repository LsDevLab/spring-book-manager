package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// @Configuration — marks this class as a source of bean definitions (like a factory)
@Configuration
public class AppConfiguration {

    @Value("${app.keycloak.base-path}")
    private String keycloakBasePath;

    // @Bean — registers the return value as a Spring-managed bean, injectable elsewhere
    @Bean
    public RestClient autoRestClient(RestClient.Builder builder) {
        // RstClienestClient.Builder is auto-provided by Spring Boot — we just set the base URL
        return builder.baseUrl("http://localhost:8081/api").build();
    }

    @Bean
    public RestClient keycloakRestClient(RestClient.Builder builder) {
        return builder.baseUrl(keycloakBasePath).build();
    }

}
