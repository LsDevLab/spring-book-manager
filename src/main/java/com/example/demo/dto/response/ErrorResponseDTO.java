package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public class ErrorResponseDTO {

    @Schema(description = "HTTP status code", example = "404")
    private int status;

    @Schema(description = "Error message", example = "Book not found")
    private String message;

    // Only populated for validation errors — maps field name → error message.
    // null (and omitted from JSON) for non-validation errors.
    @Schema(description = "Field-level validation errors (only for 400 responses)")
    private Map<String, String> errors;

    // Only populated when "dev" profile is active — full stack trace for debugging.
    // null (and omitted from JSON via @JsonInclude) in production.
    @Schema(description = "Full stack trace (dev profile only)")
    private String stackTrace;

    public ErrorResponseDTO(int status, String message){
        this.status = status;
        this.message = message;
    }

    public ErrorResponseDTO(int status, String message, Map<String, String> errors){
        this.status = status;
        this.message = message;
        this.errors = errors;
    }

}
