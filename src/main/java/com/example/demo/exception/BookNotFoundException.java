package com.example.demo.exception;

import java.util.UUID;

// Custom exception for "book not found" — extends RuntimeException (unchecked).
// Caught globally by GlobalExceptionHandler via @ControllerAdvice.
public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException(UUID id) {
        super("Book not found with id: " + id);
    }
}
