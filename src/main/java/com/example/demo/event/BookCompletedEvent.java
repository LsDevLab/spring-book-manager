package com.example.demo.event;

import java.util.UUID;

// record — immutable data carrier. Java auto-generates constructor, getters, equals, hashCode, toString.
// No Lombok needed — records handle it all.
// Access fields with: event.userId(), event.bookTitle(), etc. (no "get" prefix)
public record BookCompletedEvent(
    UUID userId,
    UUID bookId,
    String bookTitle,
    String username
) {}