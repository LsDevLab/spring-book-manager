package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// @Configuration — marks this class as a source of bean definitions (like a factory)
@Configuration
public class AppConfiguration {

    // @Bean — registers the return value as a Spring-managed bean, injectable elsewhere
    @Bean
    public RestClient get(RestClient.Builder builder) {
        // RestClient.Builder is auto-provided by Spring Boot — we just set the base URL
        return builder.baseUrl("http://localhost:8081/api").build();
    }

}
