package com.incoin.demo.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Handles ResponseStatusException thrown by services/controllers. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        log.warn("ResponseStatusException: {} {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity
            .status(ex.getStatusCode())
            .body(body(ex.getReason()));
    }

    /** Handles @Valid / @Validated constraint failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
        MethodArgumentNotValidException ex
    ) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse("Validation failed");
        return ResponseEntity.badRequest().body(body(msg));
    }

    /** Catch-all for unexpected errors — don't leak stack traces. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(body("Internal server error"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> body(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message != null ? message : "Unknown error");
        m.put("timestamp", Instant.now().toString());
        return m;
    }
}
