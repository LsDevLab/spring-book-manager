package com.example.demo.exception;

import java.util.UUID;

/**
 * Thrown when a {@link com.example.demo.model.UserBook} entry is not found
 * for the given user/book combination. Caught by {@link GlobalExceptionHandler} and mapped to 404.
 */
public class UserBookNotFoundException extends RuntimeException {
    public UserBookNotFoundException(UUID userId, UUID bookId) {
        super("User with id: " + userId + " not found or book with id: " + bookId + " not in user's reading list");
    }
}
