package com.coffeeshop.exception;

import lombok.Getter;

/**
 * Thrown when an idempotency key has already been processed with a different payload.
 * This prevents accidental duplicate operations with conflicting data.
 */
@Getter
public class IdempotencyConflictException extends RuntimeException {
    private final String idempotencyKey;
    private final Object previousPayload;
    private final Object currentPayload;

    public IdempotencyConflictException(String idempotencyKey, Object previousPayload, Object currentPayload) {
        super(String.format("Idempotency key '%s' already processed with different payload", idempotencyKey));
        this.idempotencyKey = idempotencyKey;
        this.previousPayload = previousPayload;
        this.currentPayload = currentPayload;
    }

}

