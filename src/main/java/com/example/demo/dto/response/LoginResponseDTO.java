package com.example.demo.dto.response;

// Using a record — immutable, minimal boilerplate. Just carries the JWT token back to the client.
public record LoginResponseDTO(String token) {}