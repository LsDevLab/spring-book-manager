package com.example.demo.exception;

import com.example.demo.dto.response.ErrorResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

// @ControllerAdvice — intercepts exceptions thrown by ANY controller globally.
// No more try/catch in each controller method — just throw, and this class handles it.
@ControllerAdvice
public class GlobalExceptionHandler {

    // @ExceptionHandler — maps a specific exception type to a response.
    // Any BookNotFoundException thrown anywhere returns 404 automatically.
    /** Handles {@link BookNotFoundException} → 404 Not Found. */
    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleBookNotFound(BookNotFoundException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(body);
    }

    /** Handles validation failures ({@code @Valid}) → 400 Bad Request with per-field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(), "Validation failed", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Handles malformed JSON bodies → 400 Bad Request. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadRequest(HttpMessageNotReadableException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(), "Malformed request body");
        return ResponseEntity.badRequest().body(body);
    }

    /** Handles {@link UserNotFoundException} → 404 Not Found. */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleUserNotFound(UserNotFoundException ex){
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(body);
    }

    /** Handles {@link UserBookNotFoundException} → 404 Not Found. */
    @ExceptionHandler(UserBookNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleUserBookNotFound(UserBookNotFoundException ex){
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(), "Wrong parameter type: " + ex.getPropertyName());
        return ResponseEntity.badRequest().body(body);
    }

    /** Catch-all for unhandled exceptions → 500 Internal Server Error. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneral(Exception ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error");
        return ResponseEntity.internalServerError().body(body);
    }

}
