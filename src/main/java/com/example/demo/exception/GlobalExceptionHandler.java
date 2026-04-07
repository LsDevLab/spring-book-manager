package com.example.demo.exception;

import com.example.demo.dto.response.ErrorResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// @ControllerAdvice — intercepts exceptions thrown by ANY controller globally.
// No more try/catch in each controller method — just throw, and this class handles it.
//
// In the "dev" profile, the catch-all handler also includes the full stack trace in the response
// so you don't have to dig through server logs every time. In production, stack traces are hidden.
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // Environment gives us access to the active Spring profiles at runtime.
    // We use it to decide whether to expose stack traces (dev only).
    private final Environment environment;

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

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(WrongCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleWrongCredentials(WrongCredentialsException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.FORBIDDEN.value(), "Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleRateLimitsExceeded(RateLimitExceededException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limits exceeded: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(BookAlreadyInReadingList.class)
    public ResponseEntity<ErrorResponseDTO> handleBookAlreadyInReadingList(BookAlreadyInReadingList ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.CONFLICT.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** Catch-all for unhandled exceptions → 500 Internal Server Error.
     *  In dev profile, includes the full stack trace in the response body for easy debugging. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneral(Exception ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error");

        // Environment.getActiveProfiles() returns the profiles activated via spring.profiles.active.
        // We check if "dev" is among them — if so, attach the stack trace to the response.
        // This way you see the full cause in Swagger/Postman instead of a generic 500.
        if (isDevProfile()) {
            body.setMessage(ex.getMessage());
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            body.setStackTrace(sw.toString());
        }

        return ResponseEntity.internalServerError().body(body);
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

}
