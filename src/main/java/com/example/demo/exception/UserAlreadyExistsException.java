package com.example.demo.exception;

import java.util.UUID;


public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException() {
        super("User with same username or email already exists");
    }
}
