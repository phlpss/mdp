package com.coffeeshop.web;

import com.coffeeshop.exception.EntityNotFoundException;
import com.coffeeshop.exception.IdempotencyConflictException;
import com.coffeeshop.exception.UnknownEntityTypeException;
import com.coffeeshop.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for all REST endpoints.
 * <p>
 * Converts application exceptions into standardized JSON error responses with:
 * - HTTP status codes
 * - Error codes for client identification
 * - Detailed messages and violation lists where applicable
 * - Request ID (UUID) for log correlation via MDC
 * - Timestamp for audit trails
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Handle UnknownEntityTypeException (404 Not Found).
     */
    @ExceptionHandler(UnknownEntityTypeException.class)
    public ResponseEntity<Object> handleUnknownEntityType(
            UnknownEntityTypeException ex,
            WebRequest request) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.warn("Unknown entity type: {}", ex.getEntityType());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", "UNKNOWN_TYPE");
        response.put("type", ex.getEntityType());
        response.put("message", ex.getMessage());
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("status", HttpStatus.NOT_FOUND.value());

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle ValidationException (422 Unprocessable Entity).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidationException(
            ValidationException ex,
            WebRequest request) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.warn("Validation failed with {} errors", ex.getErrors().size());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", "VALIDATION_FAILED");
        response.put("message", "Request validation failed");
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("status", 422); // Unprocessable Entity

        // Add all validation violations
        var violationsArray = response.putArray("violations");
        for (String violation : ex.getErrors()) {
            violationsArray.add(violation);
        }

        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /**
     * Handle EntityNotFoundException (404 Not Found).
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFound(
            EntityNotFoundException ex,
            WebRequest request) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.warn("Entity not found: type='{}', id='{}'", ex.getEntityType(), ex.getEntityId());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", "NOT_FOUND");
        response.put("type", ex.getEntityType());
        response.put("id", ex.getEntityId().toString());
        response.put("message", ex.getMessage());
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("status", HttpStatus.NOT_FOUND.value());

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle AccessDeniedException (403 Forbidden).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.warn("Access denied: {}", ex.getMessage());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", "FORBIDDEN");
        response.put("message", "Access denied. Insufficient permissions.");
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("status", HttpStatus.FORBIDDEN.value());

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle IdempotencyConflictException (409 Conflict).
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Object> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            WebRequest request) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.warn("Idempotency conflict for key: {}", ex.getIdempotencyKey());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", "DUPLICATE_KEY");
        response.put("idempotencyKey", ex.getIdempotencyKey());
        response.put("message", ex.getMessage());
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("status", HttpStatus.CONFLICT.value());

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Generic catch-all exception handler (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(
            Exception ex,
            WebRequest request) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.error("Unexpected exception occurred", ex);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", "INTERNAL_ERROR");
        response.put("message", "An unexpected error occurred");
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

        // Include exception type for debugging (only in non-production environments)
        response.put("exceptionType", ex.getClass().getSimpleName());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

