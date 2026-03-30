package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// Using a record — immutable, minimal boilerplate. Just carries the JWT token back to the client.
@Schema(description = "Login response containing the JWT token")
public record LoginResponseDTO(
        @Schema(description = "JWT authentication token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token
) {}
