package com.example.demo.model;

/**
 * Reading progress states for a {@link UserBook} entry.
 * Stored as STRING in the database via {@code @Enumerated(EnumType.STRING)}.
 */
public enum ReadingStatus {
    WANT_TO_READ,
    READING,
    COMPLETED
}
