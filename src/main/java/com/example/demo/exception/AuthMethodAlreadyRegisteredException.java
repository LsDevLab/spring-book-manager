package com.example.demo.exception;

import com.example.demo.model.AuthMethod;

public class AuthMethodAlreadyRegisteredException extends RuntimeException {
    public AuthMethodAlreadyRegisteredException(AuthMethod authMethod) {
        super("User is already registered with auth method: " + authMethod);
    }
}