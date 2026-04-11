package com.coffeeshop.exception;

import lombok.Getter;

import java.util.List;

/**
 * Thrown when validation of an entity payload fails against its MetaType schema.
 * Contains a list of all validation violations (not just the first error) to provide
 * comprehensive feedback to the client.
 */
@Getter
public class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Validation failed with " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

}

