package com.example.demo.exception;

import java.util.UUID;

/**
 * Thrown when a {@link com.example.demo.model.User} with the given ID does not exist.
 * Caught by {@link GlobalExceptionHandler} and mapped to a 404 response.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found with id: " + id);
    }
}
