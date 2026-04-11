package com.coffeeshop.exception;

import lombok.Getter;

/**
 * Thrown when an entity type referenced in a request is not defined in the metadata cache.
 * This is a client error (4xx) and typically indicates the caller is using an outdated
 * or incorrect entity type name.
 */
@Getter
public class UnknownEntityTypeException extends RuntimeException {
    private final String entityType;

    public UnknownEntityTypeException(String entityType) {
        super("Unknown entity type: " + entityType);
        this.entityType = entityType;
    }

}

