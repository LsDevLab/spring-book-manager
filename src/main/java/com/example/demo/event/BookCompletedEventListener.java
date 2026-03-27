package com.example.demo.event;

import com.example.demo.event.BookCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BookCompletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookCompletedEventListener.class);

    @Async
    @EventListener
    public void handleBookCompleted(BookCompletedEvent event) {
        log.info(String.valueOf(event));
    }
}
