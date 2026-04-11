package com.coffeeshop.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Thrown when a requested entity is not found in the database.
 * Includes the entity type and UUID for detailed error reporting.
 */
@Getter
public class EntityNotFoundException extends RuntimeException {
    private final String entityType;
    private final UUID entityId;

    public EntityNotFoundException(String entityType, UUID entityId) {
        super(String.format("Entity of type '%s' with ID '%s' not found", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

}

