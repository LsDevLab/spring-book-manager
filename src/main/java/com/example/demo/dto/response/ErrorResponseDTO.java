package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
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

    public ErrorResponseDTO(int status, String message){
        this.status = status;
        this.message = message;
    }

}
