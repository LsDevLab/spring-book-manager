package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDTO {

    private int status;

    private String message;

    // Only populated for validation errors — maps field name → error message.
    // null (and omitted from JSON) for non-validation errors.
    private Map<String, String> errors;

    public ErrorResponseDTO(int status, String message){
        this.status = status;
        this.message = message;
    }

}
