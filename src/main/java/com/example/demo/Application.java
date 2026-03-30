package com.example.demo;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
// It auto-configures beans based on classpath (e.g., DataSource from spring.datasource props)
// and scans this package + subpackages for @Component, @Service, @Repository, @Controller
@SpringBootApplication
@EnableScheduling     // Activates @Scheduled methods across the app (see ScheduledTasks.java)
@EnableCaching
@EnableAsync
@OpenAPIDefinition(
    info = @Info(
        title = "Book Manager API",
        version = "1.0",
        description = "Spring Boot REST API for managing a personal book reading list"
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Enter your JWT token obtained from /api/auth/login"
)
public class Application {

	// Entry point — boots embedded Tomcat, creates Spring context, wires all beans
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}