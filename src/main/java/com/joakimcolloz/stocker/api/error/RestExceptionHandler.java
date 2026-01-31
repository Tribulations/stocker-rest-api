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

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<Map<String, Object>> handleAnalysisException(AnalysisException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", ex.getCode().name(),
                        "message", ex.getMessage(),
                        "details", ex.getDetails()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
		return ResponseEntity.badRequest().build();
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Void> handleConstraintViolationException(ConstraintViolationException ex) {
		return ResponseEntity.badRequest().build();
	}
}
