package com.joakimcolloz.stocker.api.error;

import com.joakimcolloz.stocker.analysis.error.AnalysisException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

/**
 * Global REST exception handler that converts known server-side calculation errors into client-friendly
 * HTTP responses.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    /**
     * Handles {@link AnalysisException} by returning a 400 response containing
     * the error code, message, and any supplementary details.
     */
    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<Map<String, Object>> handleAnalysisException(AnalysisException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", ex.getCode().name(),
                        "message", ex.getMessage(),
                        "details", ex.getDetails()
                ));
    }

    /**
     * Handles {@link IllegalStateException} by returning a 400 response with the exception message.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

	/**
	 * Handles {@link MethodArgumentNotValidException} (Bean Validation failures on {@code @RequestBody})
	 * by returning an empty 400 response.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
		// TODO; Change this later: Intentionally return an empty-body 400 to preserve previous controller behavior
		// (controllers used ResponseEntity.badRequest().build() for validation failures).
		return ResponseEntity.badRequest().build();
	}

	/**
	 * Handles {@link ConstraintViolationException} (Bean Validation failures on method parameters)
	 * by returning an empty 400 response.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Void> handleConstraintViolationException(ConstraintViolationException ex) {
		// TODO; Change this later: Intentionally return an empty-body 400 to preserve previous controller behavior.
		return ResponseEntity.badRequest().build();
	}

	/**
	 * Handles {@link IllegalArgumentException} by returning an empty 400 response.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().build();
	}
}
