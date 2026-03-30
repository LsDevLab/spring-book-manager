package com.example.demo.exception;


public class WrongCredentialsException extends RuntimeException {
    public WrongCredentialsException() {
        super("Wrong credentials");
    }
}
