package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RestController;

// @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
// It auto-configures beans based on classpath (e.g., DataSource from spring.datasource props)
// and scans this package + subpackages for @Component, @Service, @Repository, @Controller
@SpringBootApplication
@RestController       // Not needed here — no endpoints defined in this class. Leftover from demo.
@EnableScheduling     // Activates @Scheduled methods across the app (see ScheduledTasks.java)
@EnableCaching
@EnableAsync
public class Application {

	// Entry point — boots embedded Tomcat, creates Spring context, wires all beans
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}