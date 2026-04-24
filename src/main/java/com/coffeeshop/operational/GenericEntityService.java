package com.coffeeshop.operational;

import com.coffeeshop.metadata.MetaType;
import com.coffeeshop.metadata.MetadataCacheService;
import com.coffeeshop.security.UserPrincipal;
import com.coffeeshop.validation.ValidationEngineService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for generic entity operations (CRUD).
 *
 * Enforces:
 * 1. Validation against MetaType schema before any write
 * 2. Field-level masking based on caller's role before returning data
 * 3. Asynchronous Pub/Sub event publishing after successful writes
 * 4. Audit logging of all operations
 */
@Slf4j
@Service
@Transactional
public class GenericEntityService {
    private final EntityDataRepository entityDataRepository;
    private final MetadataCacheService metadataCacheService;
    private final ValidationEngineService validationEngineService;
//    private final PubSubPublisherService pubSubPublisherService;
    private final ObjectMapper objectMapper;

    public GenericEntityService(EntityDataRepository entityDataRepository, MetadataCacheService metadataCacheService, ValidationEngineService validationEngineService, ObjectMapper objectMapper) {
        this.entityDataRepository = entityDataRepository;
        this.metadataCacheService = metadataCacheService;
        this.validationEngineService = validationEngineService;
//        this.pubSubPublisherService = pubSubPublisherService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new entity with the given type and payload.
     *
     * @param type The entity type (e.g., "Employee", "Transaction")
     * @param payload The entity payload
     * @param caller The authenticated user
     * @return The created entity with masking applied
     */
    public EntityData create(String type, JsonNode payload, UserPrincipal caller) {
        log.info("Creating entity of type '{}' by user: {}", type, caller.getUsername());

        // Validate against schema
        validationEngineService.validate(type, payload);

        // Create entity
        EntityData entity = EntityData.builder().id(UUID.randomUUID()).type(type).payload(payload).build();

        EntityData saved = entityDataRepository.save(entity);
        log.info("Entity created: type='{}', id='{}'", type, saved.getId());

        // todo: Publish event asynchronously (non-blocking)
//        pubSubPublisherService.publishEntityEvent("CREATED", type, saved.getId(), payload);

        // Apply masking before returning
        return applyFieldMask(type, saved, caller);
    }

    /**
     * Retrieve paginated entities of a given type.
     *
     * @param type The entity type
     * @param pageable Pagination parameters
     * @param caller The authenticated user
     * @return Page of entities with masking applied
     */
    public Page<EntityData> findAll(String type, Pageable pageable, UserPrincipal caller) {
        log.debug("Fetching page of entities: type='{}', page={}, size={}", type, pageable.getPageNumber(), pageable.getPageSize());

        metadataCacheService.getType(type); // Validate type exists

        Page<EntityData> page = entityDataRepository.findByType(type, pageable);

        // Apply masking to each entity
        return page.map(entity -> applyFieldMask(type, entity, caller));
    }

    /**
     * Retrieve a single entity by ID.
     *
     * @param type The entity type
     * @param id The entity UUID
     * @param caller The authenticated user
     * @return The entity with masking applied
     */
    public EntityData findById(String type, UUID id, UserPrincipal caller) {
        log.debug("Fetching entity: type='{}', id='{}'", type, id);

        metadataCacheService.getType(type); // Validate type exists

        EntityData entity = entityDataRepository.findByTypeAndId(type, id);
        if (entity == null) {
            throw new com.coffeeshop.exception.EntityNotFoundException(type, id);
        }

        return applyFieldMask(type, entity, caller);
    }

    /**
     * Update an entity.
     *
     * @param type The entity type
     * @param id The entity UUID
     * @param payload The updated payload
     * @param caller The authenticated user
     * @return The updated entity with masking applied
     */
    public EntityData update(String type, UUID id, JsonNode payload, UserPrincipal caller) {
        log.info("Updating entity: type='{}', id='{}' by user: {}", type, id, caller.getUsername());

        // Validate against schema
        validationEngineService.validate(type, payload);

        EntityData entity = entityDataRepository.findByTypeAndId(type, id);
        if (entity == null) {
            throw new com.coffeeshop.exception.EntityNotFoundException(type, id);
        }

        entity.setPayload(payload);
        EntityData updated = entityDataRepository.save(entity);
        log.info("Entity updated: type='{}', id='{}'", type, id);

//        todo
//        pubSubPublisherService.publishEntityEvent("UPDATED", type, updated.getId(), payload);

        // Apply masking before returning
        return applyFieldMask(type, updated, caller);
    }

    /**
     * Delete an entity.
     *
     * @param type The entity type
     * @param id The entity UUID
     * @param caller The authenticated user
     */
    public void delete(String type, UUID id, UserPrincipal caller) {
        log.info("Deleting entity: type='{}', id='{}' by user: {}", type, id, caller.getUsername());

        EntityData entity = entityDataRepository.findByTypeAndId(type, id);
        if (entity == null) {
            throw new com.coffeeshop.exception.EntityNotFoundException(type, id);
        }

        entityDataRepository.deleteById(id);
        log.info("Entity deleted: type='{}', id='{}'", type, id);

//        todo
//        pubSubPublisherService.publishEntityEvent("DELETED", type, id, entity.getPayload());
    }

    /**
     * Apply field-level masking to an entity based on the caller's role.
     *
     * Sensitive fields are masked based on:
     * 1. The field's inclusion in MetaType.sensitiveFields
     * 2. The caller's role (STORE_MANAGER and ACCOUNTANT can see all fields)
     * 3. Whether the caller owns the record (e.g., employee can see their own salary)
     *
     * @param type The entity type
     * @param entity The entity to mask
     * @param caller The authenticated user
     * @return Entity with masked fields, or original if no masking applies
     */
    private EntityData applyFieldMask(String type, EntityData entity, UserPrincipal caller) {
        MetaType metaType = metadataCacheService.findType(type).orElse(null);
        if (metaType == null || metaType.sensitiveFields() == null || metaType.sensitiveFields().isEmpty()) {
            return entity;
        }

        // Check if caller has permission to view sensitive fields
        boolean canViewSensitive = caller.hasAnyRole(com.coffeeshop.security.Role.STORE_MANAGER, com.coffeeshop.security.Role.ACCOUNTANT);

        if (canViewSensitive) {
            return entity;
        }

        // Check if caller owns the entity (employeeId field)
        boolean isOwner = false;
        if (entity.getPayload().has("employeeId")) {
            String payloadEmployeeId = entity.getPayload().get("employeeId").asText();
            isOwner = payloadEmployeeId.equals(caller.getUserId().toString());
        }

        if (isOwner) {
            return entity;
        }

        // Mask sensitive fields
        EntityData masked = new EntityData();
        masked.setId(entity.getId());
        masked.setType(entity.getType());
        masked.setCreatedAt(entity.getCreatedAt());
        masked.setUpdatedAt(entity.getUpdatedAt());

        ObjectNode maskedPayload = objectMapper.createObjectNode();

        entity.getPayload().properties().iterator().forEachRemaining(entry -> {
            if (metaType.sensitiveFields().contains(entry.getKey())) {
                maskedPayload.put(entry.getKey(), "***MASKED***");
            } else {
                maskedPayload.set(entry.getKey(), entry.getValue());
            }
        });

        masked.setPayload(maskedPayload);
        return masked;
    }
}

