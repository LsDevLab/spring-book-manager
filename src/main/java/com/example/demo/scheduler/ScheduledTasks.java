package com.example.demo.scheduler;

import com.example.demo.dto.request.BookRequestDTO;
import com.example.demo.model.Book;
import com.example.demo.model.BookIsbnPolicy;
import com.example.demo.model.ReadingStatus;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.UserBookRepository;
import com.example.demo.service.BookService;
import com.example.demo.service.UserBookService;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.springframework.web.client.RestClient;

// @Component — generic Spring bean (auto-detected by @ComponentScan)
// Requires @EnableScheduling on the Application class to activate @Scheduled methods
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final UserBookService userBookService;
    private final BookService bookService;
    private final RestClient autoRestClient;

    // SLF4J logger — Spring Boot's default logging facade
    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    // @Scheduled fixedRate = 5000 — runs every 5 seconds (in milliseconds)
    // Alternative: cron = "0 0 2 * * *" — runs at 2am daily (see Phase 7)
    @Scheduled(fixedRate = 60000)
    public void logActiveReadingSessions() {
        long count = userBookService.countByStatus(ReadingStatus.READING);
        log.info("Active reading sessions: {}", count);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void fetchTrendingBooks() {
        List<BookRequestDTO> books = autoRestClient.get()
                .uri("/mock/trending-books")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if(books != null){
            bookService.createBooks(
                    books.stream().map(BookRequestDTO::toEntity).toList(),
                    BookIsbnPolicy.ISBN_AS_ALERNATIVE_IDENTIFIER
            );
        }
    }

}